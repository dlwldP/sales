package com.multicloud.quote.config;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        PriceSync priceSync,
        Vendors vendors,
        Storage storage
) {

    /**
     * @param regions 동기화 대상 논리 리전 키 (RegionCatalog 기준)
     * @param osTypes 동기화 대상 OS
     */
    public record PriceSync(
            @DefaultValue("0 0 3 * * *") String cron,
            @DefaultValue("Asia/Seoul") String zone,
            @DefaultValue({"korea"}) List<String> regions,
            @DefaultValue({"LINUX"}) List<OsType> osTypes,
            @DefaultValue("false") boolean runOnStartup,
            @DefaultValue("20") int maxPages
    ) {
    }

    public record Vendors(Aws aws, Azure azure, Gcp gcp) {

        public record Aws(
                @DefaultValue("true") boolean enabled,
                /* Pricing API 엔드포인트는 us-east-1 고정 */
                @DefaultValue("us-east-1") String regionCode
        ) {
        }

        public record Azure(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("https://prices.azure.com/api/retail/prices") String baseUrl,
                @DefaultValue("USD") String currencyCode
        ) {
        }

        public record Gcp(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("https://cloudbilling.googleapis.com/v1") String baseUrl,
                @DefaultValue("") String apiKey
        ) {
        }
    }

    /**
     * 블록 스토리지 단가(USD/GB·월). 공개 가격 API의 스토리지 SKU 구조가 벤더마다 크게 달라
     * MVP 단계에서는 벤더별 표준 SSD 단가를 설정값으로 두고 견적에 가산한다.
     */
    public record Storage(Map<VendorType, BigDecimal> rateUsdPerGbMonth) {

        public BigDecimal rateFor(VendorType vendor) {
            if (rateUsdPerGbMonth == null) {
                return BigDecimal.ZERO;
            }
            return rateUsdPerGbMonth.getOrDefault(vendor, BigDecimal.ZERO);
        }
    }
}
