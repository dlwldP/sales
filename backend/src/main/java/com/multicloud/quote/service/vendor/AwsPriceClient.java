package com.multicloud.quote.service.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.exception.PriceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.pricing.PricingClient;
import software.amazon.awssdk.services.pricing.model.Filter;
import software.amazon.awssdk.services.pricing.model.FilterType;
import software.amazon.awssdk.services.pricing.model.GetProductsRequest;
import software.amazon.awssdk.services.pricing.model.GetProductsResponse;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AWS Price List Query API(GetProducts) 클라이언트.
 * 응답 priceList는 중첩 JSON 문자열 배열이므로 terms.OnDemand → priceDimensions → pricePerUnit.USD를 파싱한다.
 *
 * @see <a href="https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_pricing_GetProducts.html">GetProducts API Reference</a>
 */
@Component
public class AwsPriceClient implements VendorPriceClient {

    private static final Logger log = LoggerFactory.getLogger(AwsPriceClient.class);

    private static final String SERVICE_CODE = "AmazonEC2";
    private static final int PAGE_SIZE = 100;

    private final ObjectProvider<PricingClient> pricingClientProvider;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public AwsPriceClient(ObjectProvider<PricingClient> pricingClientProvider,
                          AppProperties properties,
                          ObjectMapper objectMapper) {
        this.pricingClientProvider = pricingClientProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public VendorType vendor() {
        return VendorType.AWS;
    }

    @Override
    public boolean isEnabled() {
        return properties.vendors().aws().enabled();
    }

    @Override
    public List<InstancePrice> fetchComputePrices(String vendorRegionCode, OsType os) {
        PricingClient client = pricingClientProvider.getIfAvailable();
        if (client == null) {
            throw new PriceApiException(VendorType.AWS, "PricingClient 빈이 비활성화되어 있습니다.");
        }

        Map<String, InstancePrice> collected = new LinkedHashMap<>();
        String nextToken = null;
        int page = 0;
        int maxPages = properties.priceSync().maxPages();

        try {
            do {
                GetProductsRequest request = GetProductsRequest.builder()
                        .serviceCode(SERVICE_CODE)
                        .filters(filters(vendorRegionCode, os))
                        .maxResults(PAGE_SIZE)
                        .nextToken(nextToken)
                        .build();

                GetProductsResponse response = client.getProducts(request);
                for (String priceListJson : response.priceList()) {
                    parse(priceListJson, vendorRegionCode, os)
                            .ifPresent(price -> collected.merge(price.sku(), price,
                                    (a, b) -> a.hourlyCostUsd().compareTo(b.hourlyCostUsd()) <= 0 ? a : b));
                }
                nextToken = response.nextToken();
                page++;
            } while (nextToken != null && !nextToken.isBlank() && page < maxPages);
        } catch (SdkException e) {
            throw new PriceApiException(VendorType.AWS,
                    "Price List Query API 호출 실패 (region=" + vendorRegionCode + ")", e);
        }

        log.info("AWS 가격 {}건 수집 완료. region={}, os={}", collected.size(), vendorRegionCode, os);
        return List.copyOf(collected.values());
    }

    private List<Filter> filters(String vendorRegionCode, OsType os) {
        return List.of(
                termMatch("regionCode", vendorRegionCode),
                termMatch("operatingSystem", os == OsType.WINDOWS ? "Windows" : "Linux"),
                termMatch("tenancy", "Shared"),
                termMatch("preInstalledSw", "NA"),
                // capacitystatus=Used 로 온디맨드 실사용 단가만 남긴다 (예약 용량 SKU 제외)
                termMatch("capacitystatus", "Used"),
                termMatch("marketoption", "OnDemand")
        );
    }

    private Filter termMatch(String field, String value) {
        return Filter.builder().type(FilterType.TERM_MATCH).field(field).value(value).build();
    }

    private Optional<InstancePrice> parse(String priceListJson, String regionCode, OsType os) {
        try {
            JsonNode root = objectMapper.readTree(priceListJson);
            JsonNode attributes = root.path("product").path("attributes");

            String instanceType = attributes.path("instanceType").asText(null);
            Integer vcpu = parseInt(attributes.path("vcpu").asText(null));
            Integer memoryGb = parseMemoryGb(attributes.path("memory").asText(null));
            if (instanceType == null || vcpu == null || memoryGb == null) {
                return Optional.empty();
            }

            return findOnDemandHourlyUsd(root)
                    .map(price -> new InstancePrice(instanceType, vcpu, memoryGb, regionCode, os, price));
        } catch (Exception e) {
            log.debug("AWS priceList 항목 파싱 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** terms.OnDemand.{offerTerm}.priceDimensions.{rateCode}.pricePerUnit.USD 중 유효한 시간 단가를 찾는다. */
    private Optional<BigDecimal> findOnDemandHourlyUsd(JsonNode root) {
        JsonNode onDemand = root.path("terms").path("OnDemand");
        for (JsonNode offerTerm : onDemand) {
            for (JsonNode dimension : offerTerm.path("priceDimensions")) {
                String unit = dimension.path("unit").asText("");
                String usd = dimension.path("pricePerUnit").path("USD").asText(null);
                if (!"Hrs".equalsIgnoreCase(unit) || usd == null || usd.isBlank()) {
                    continue;
                }
                try {
                    BigDecimal price = new BigDecimal(usd);
                    if (price.signum() > 0) {
                        return Optional.of(price);
                    }
                } catch (NumberFormatException ignored) {
                    // 다음 priceDimension 시도
                }
            }
        }
        return Optional.empty();
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "16 GiB" 형태의 문자열에서 GB 값을 뽑는다. 1GB 미만은 1GB로 올림한다. */
    private Integer parseMemoryGb(String raw) {
        if (raw == null || raw.isBlank() || raw.toLowerCase().contains("na")) {
            return null;
        }
        String numeric = raw.replaceAll("[^0-9.]", "");
        if (numeric.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(numeric);
            return value < 1 ? 1 : (int) Math.round(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
