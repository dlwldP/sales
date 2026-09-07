package com.multicloud.quote.service.vendor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.exception.PriceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Azure Retail Prices API 클라이언트. 인증이 필요 없는 공개 API이며,
 * 응답의 NextPageLink를 따라가며 페이지(최대 1,000건)를 모두 수집한다.
 *
 * @see <a href="https://learn.microsoft.com/en-us/rest/api/cost-management/retail-prices/azure-retail-prices">Azure Retail Prices API</a>
 */
@Component
public class AzurePriceClient implements VendorPriceClient {

    private static final Logger log = LoggerFactory.getLogger(AzurePriceClient.class);

    private static final String SERVICE_NAME = "Virtual Machines";
    private static final String CONSUMPTION = "Consumption";
    private static final String HOURLY_UNIT = "1 Hour";

    private final RestClient restClient;
    private final AppProperties properties;
    private final AzureSkuSpecResolver skuSpecResolver;

    public AzurePriceClient(RestClient.Builder restClientBuilder,
                            AppProperties properties,
                            AzureSkuSpecResolver skuSpecResolver) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.skuSpecResolver = skuSpecResolver;
    }

    @Override
    public VendorType vendor() {
        return VendorType.AZURE;
    }

    @Override
    public boolean isEnabled() {
        return properties.vendors().azure().enabled();
    }

    @Override
    public List<InstancePrice> fetchComputePrices(String vendorRegionCode, OsType os) {
        AppProperties.Vendors.Azure config = properties.vendors().azure();
        String url = buildFirstPageUrl(config, vendorRegionCode);

        Map<String, InstancePrice> collected = new LinkedHashMap<>();
        int page = 0;
        int maxPages = properties.priceSync().maxPages();

        while (url != null && page < maxPages) {
            RetailPricesResponse response = get(url, vendorRegionCode);
            if (response == null || response.items() == null) {
                break;
            }
            for (RetailPriceItem item : response.items()) {
                toInstancePrice(item, vendorRegionCode, os)
                        // 동일 SKU가 여러 미터로 반복 등장하므로 가장 저렴한 단가를 유지한다.
                        .ifPresent(price -> collected.merge(price.sku(), price,
                                (a, b) -> a.hourlyCostUsd().compareTo(b.hourlyCostUsd()) <= 0 ? a : b));
            }
            url = response.nextPageLink();
            page++;
        }

        if (page >= maxPages && url != null) {
            log.warn("Azure 가격 조회가 최대 페이지({})에 도달해 중단되었습니다. region={}", maxPages, vendorRegionCode);
        }
        log.info("Azure 가격 {}건 수집 완료. region={}, os={}", collected.size(), vendorRegionCode, os);
        return List.copyOf(collected.values());
    }

    private String buildFirstPageUrl(AppProperties.Vendors.Azure config, String vendorRegionCode) {
        String filter = "serviceName eq '" + SERVICE_NAME + "'"
                + " and armRegionName eq '" + vendorRegionCode + "'"
                + " and priceType eq '" + CONSUMPTION + "'";
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(config.baseUrl())
                .queryParam("currencyCode", config.currencyCode())
                .queryParam("$filter", filter)
                .build()
                .encode()
                .toUriString();
    }

    private RetailPricesResponse get(String url, String vendorRegionCode) {
        try {
            return restClient.get()
                    .uri(java.net.URI.create(url))
                    .retrieve()
                    .body(RetailPricesResponse.class);
        } catch (RestClientException e) {
            throw new PriceApiException(VendorType.AZURE,
                    "Retail Prices API 호출 실패 (region=" + vendorRegionCode + ")", e);
        }
    }

    private Optional<InstancePrice> toInstancePrice(RetailPriceItem item, String regionCode, OsType os) {
        if (item == null || item.armSkuName() == null || item.retailPrice() == null) {
            return Optional.empty();
        }
        if (!CONSUMPTION.equalsIgnoreCase(item.type()) || !HOURLY_UNIT.equalsIgnoreCase(item.unitOfMeasure())) {
            return Optional.empty();
        }
        if (item.retailPrice().signum() <= 0) {
            return Optional.empty();
        }
        // 스팟/저우선순위 미터는 온디맨드 견적 대상이 아니다.
        String meterName = item.meterName() == null ? "" : item.meterName();
        if (meterName.contains("Spot") || meterName.contains("Low Priority")) {
            return Optional.empty();
        }
        if (!matchesOs(item.productName(), os)) {
            return Optional.empty();
        }

        return skuSpecResolver.resolve(item.armSkuName())
                .map(spec -> new InstancePrice(
                        item.armSkuName(),
                        spec.vcpu(),
                        spec.memoryGb(),
                        regionCode,
                        os,
                        item.retailPrice()));
    }

    private boolean matchesOs(String productName, OsType os) {
        boolean windowsProduct = productName != null && productName.contains("Windows");
        return os == OsType.WINDOWS ? windowsProduct : !windowsProduct;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RetailPricesResponse(
            @JsonProperty("BillingCurrency") String billingCurrency,
            @JsonProperty("Items") List<RetailPriceItem> items,
            @JsonProperty("NextPageLink") String nextPageLink,
            @JsonProperty("Count") Integer count
    ) {
        RetailPricesResponse {
            items = items == null ? new ArrayList<>() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RetailPriceItem(
            String armSkuName,
            String armRegionName,
            BigDecimal retailPrice,
            String unitOfMeasure,
            String productName,
            String meterName,
            String skuName,
            String type
    ) {
    }
}
