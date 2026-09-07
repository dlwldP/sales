package com.multicloud.quote.service.vendor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.exception.PriceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GCP Cloud Billing Catalog API 클라이언트 (Phase 2).
 *
 * <p>GCP는 AWS/Azure와 달리 머신 타입 단위 단가가 아니라 vCPU(Core)/메모리(Ram) SKU가 분리되어 있다.
 * 따라서 Compute Engine 서비스의 Core/Ram SKU 단가를 패밀리별로 수집한 뒤,
 * 표준 머신 형태(n2-standard-4 등)의 단가를 (코어단가 × vCPU) + (메모리단가 × GB)로 합성한다.
 *
 * <p>API Key가 필요하므로 기본값은 비활성(app.vendors.gcp.enabled=false)이다.
 *
 * @see <a href="https://docs.cloud.google.com/billing/v1/how-tos/catalog-api">Cloud Billing Catalog API</a>
 */
@Component
public class GcpPriceClient implements VendorPriceClient {

    private static final Logger log = LoggerFactory.getLogger(GcpPriceClient.class);

    private static final String COMPUTE_ENGINE = "Compute Engine";
    private static final int PAGE_SIZE = 5000;

    /** 예: "N2 Instance Core running in Seoul", "N1 Predefined Instance Ram running in Seoul" */
    private static final Pattern SKU_DESCRIPTION =
            Pattern.compile("^([A-Z][0-9A-Z]*)(?: Predefined)? Instance (Core|Ram) running in .+$");

    /** 합성 대상 머신 형태: 접미사 → vCPU당 메모리(GB) */
    private static final Map<String, Double> SHAPES = Map.of(
            "standard", 4.0,
            "highmem", 8.0,
            "highcpu", 1.0
    );

    private static final List<Integer> VCPU_SIZES = List.of(2, 4, 8, 16, 32, 64);

    private final RestClient restClient;
    private final AppProperties properties;

    public GcpPriceClient(RestClient.Builder restClientBuilder, AppProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public VendorType vendor() {
        return VendorType.GCP;
    }

    @Override
    public boolean isEnabled() {
        AppProperties.Vendors.Gcp config = properties.vendors().gcp();
        return config.enabled() && config.apiKey() != null && !config.apiKey().isBlank();
    }

    @Override
    public List<InstancePrice> fetchComputePrices(String vendorRegionCode, OsType os) {
        if (!isEnabled()) {
            throw new PriceApiException(VendorType.GCP, "GCP 연동이 비활성화되어 있거나 API Key가 없습니다.");
        }
        if (os == OsType.WINDOWS) {
            // Windows 라이선스는 별도 SKU(Licensing 리소스 패밀리)로 과금되어 Phase 2 범위 밖이다.
            log.info("GCP Windows 견적은 아직 지원하지 않습니다.");
            return List.of();
        }

        String serviceId = findComputeEngineServiceId();
        Map<String, FamilyRate> rates = collectFamilyRates(serviceId, vendorRegionCode);

        List<InstancePrice> prices = new ArrayList<>();
        rates.forEach((family, rate) -> {
            if (!rate.isComplete()) {
                return;
            }
            SHAPES.forEach((shape, memoryPerVcpu) -> VCPU_SIZES.forEach(vcpu -> {
                int memoryGb = (int) Math.round(vcpu * memoryPerVcpu);
                BigDecimal hourly = rate.core().multiply(BigDecimal.valueOf(vcpu))
                        .add(rate.ram().multiply(BigDecimal.valueOf(memoryGb)))
                        .setScale(6, RoundingMode.HALF_UP);
                if (hourly.signum() > 0) {
                    prices.add(new InstancePrice(
                            family + "-" + shape + "-" + vcpu, vcpu, memoryGb, vendorRegionCode, os, hourly));
                }
            }));
        });

        log.info("GCP 가격 {}건 합성 완료. region={}, families={}", prices.size(), vendorRegionCode, rates.keySet());
        return prices;
    }

    private String findComputeEngineServiceId() {
        String url = UriComponentsBuilder.fromUriString(properties.vendors().gcp().baseUrl() + "/services")
                .queryParam("key", properties.vendors().gcp().apiKey())
                .queryParam("pageSize", 200)
                .build()
                .toUriString();

        String pageToken = null;
        do {
            String pagedUrl = pageToken == null ? url : url + "&pageToken=" + pageToken;
            ServicesResponse response = get(pagedUrl, ServicesResponse.class);
            if (response == null) {
                break;
            }
            for (Service service : response.servicesOrEmpty()) {
                if (COMPUTE_ENGINE.equalsIgnoreCase(service.displayName())) {
                    return service.name(); // "services/6F81-5844-456A"
                }
            }
            pageToken = response.nextPageToken();
        } while (pageToken != null && !pageToken.isBlank());

        throw new PriceApiException(VendorType.GCP, "Compute Engine 서비스를 카탈로그에서 찾지 못했습니다.");
    }

    private Map<String, FamilyRate> collectFamilyRates(String serviceName, String vendorRegionCode) {
        String baseUrl = UriComponentsBuilder
                .fromUriString(properties.vendors().gcp().baseUrl() + "/" + serviceName + "/skus")
                .queryParam("key", properties.vendors().gcp().apiKey())
                .queryParam("pageSize", PAGE_SIZE)
                .build()
                .toUriString();

        Map<String, FamilyRate> rates = new LinkedHashMap<>();
        String pageToken = null;
        int page = 0;
        int maxPages = properties.priceSync().maxPages();

        do {
            String url = pageToken == null ? baseUrl : baseUrl + "&pageToken=" + pageToken;
            SkusResponse response = get(url, SkusResponse.class);
            if (response == null) {
                break;
            }
            for (Sku sku : response.skusOrEmpty()) {
                applySku(sku, vendorRegionCode, rates);
            }
            pageToken = response.nextPageToken();
            page++;
        } while (pageToken != null && !pageToken.isBlank() && page < maxPages);

        return rates;
    }

    private void applySku(Sku sku, String vendorRegionCode, Map<String, FamilyRate> rates) {
        if (sku == null || sku.description() == null || sku.category() == null) {
            return;
        }
        if (!"Compute".equalsIgnoreCase(sku.category().resourceFamily())
                || !"OnDemand".equalsIgnoreCase(sku.category().usageType())) {
            return;
        }
        if (sku.serviceRegions() == null || !sku.serviceRegions().contains(vendorRegionCode)) {
            return;
        }
        Matcher matcher = SKU_DESCRIPTION.matcher(sku.description());
        if (!matcher.matches()) {
            return;
        }

        String family = matcher.group(1).toLowerCase(Locale.ROOT);
        boolean isCore = "Core".equalsIgnoreCase(matcher.group(2));
        BigDecimal unitPrice = unitPriceUsd(sku);
        if (unitPrice == null) {
            return;
        }

        FamilyRate current = rates.getOrDefault(family, FamilyRate.empty());
        rates.put(family, isCore ? current.withCore(unitPrice) : current.withRam(unitPrice));
    }

    /** pricingInfo[0].pricingExpression.tieredRates 중 마지막 구간의 unitPrice(units + nanos)를 USD로 환산. */
    private BigDecimal unitPriceUsd(Sku sku) {
        if (sku.pricingInfo() == null || sku.pricingInfo().isEmpty()) {
            return null;
        }
        PricingExpression expression = sku.pricingInfo().get(sku.pricingInfo().size() - 1).pricingExpression();
        if (expression == null || expression.tieredRates() == null || expression.tieredRates().isEmpty()) {
            return null;
        }
        Money price = expression.tieredRates().get(expression.tieredRates().size() - 1).unitPrice();
        if (price == null) {
            return null;
        }
        BigDecimal units = new BigDecimal(price.units() == null ? "0" : price.units());
        BigDecimal nanos = BigDecimal.valueOf(price.nanos() == null ? 0 : price.nanos())
                .movePointLeft(9);
        BigDecimal total = units.add(nanos);
        return total.signum() > 0 ? total : null;
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return restClient.get().uri(java.net.URI.create(url)).retrieve().body(type);
        } catch (RestClientException e) {
            throw new PriceApiException(VendorType.GCP, "Cloud Billing Catalog API 호출 실패", e);
        }
    }

    private record FamilyRate(BigDecimal core, BigDecimal ram) {

        static FamilyRate empty() {
            return new FamilyRate(null, null);
        }

        FamilyRate withCore(BigDecimal core) {
            return new FamilyRate(core, ram);
        }

        FamilyRate withRam(BigDecimal ram) {
            return new FamilyRate(core, ram);
        }

        boolean isComplete() {
            return core != null && ram != null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ServicesResponse(List<Service> services, String nextPageToken) {
        List<Service> servicesOrEmpty() {
            return services == null ? List.of() : services;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Service(String name, String serviceId, String displayName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SkusResponse(List<Sku> skus, String nextPageToken) {
        List<Sku> skusOrEmpty() {
            return skus == null ? List.of() : skus;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sku(String skuId, String description, Category category, List<String> serviceRegions,
               List<PricingInfo> pricingInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Category(String serviceDisplayName, String resourceFamily, String resourceGroup, String usageType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PricingInfo(PricingExpression pricingExpression) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PricingExpression(String usageUnit, List<TieredRate> tieredRates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TieredRate(Integer startUsageAmount, Money unitPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Money(String currencyCode, String units, Integer nanos) {
    }
}
