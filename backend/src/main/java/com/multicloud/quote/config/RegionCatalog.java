package com.multicloud.quote.config;

import com.multicloud.quote.entity.VendorType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 논리 리전 키(korea 등)를 벤더별 리전 코드로 매핑한다.
 * 견적 요청은 논리 키를 쓰고, 외부 API 호출/스냅샷 저장은 벤더 코드를 쓴다.
 */
@Component
public class RegionCatalog {

    private static final Map<String, Map<VendorType, String>> REGIONS = new LinkedHashMap<>();

    static {
        REGIONS.put("korea", Map.of(
                VendorType.AWS, "ap-northeast-2",
                VendorType.AZURE, "koreacentral",
                VendorType.GCP, "asia-northeast3"));
        REGIONS.put("tokyo", Map.of(
                VendorType.AWS, "ap-northeast-1",
                VendorType.AZURE, "japaneast",
                VendorType.GCP, "asia-northeast1"));
        REGIONS.put("singapore", Map.of(
                VendorType.AWS, "ap-southeast-1",
                VendorType.AZURE, "southeastasia",
                VendorType.GCP, "asia-southeast1"));
        REGIONS.put("us-east", Map.of(
                VendorType.AWS, "us-east-1",
                VendorType.AZURE, "eastus",
                VendorType.GCP, "us-east4"));
        REGIONS.put("west-europe", Map.of(
                VendorType.AWS, "eu-west-1",
                VendorType.AZURE, "westeurope",
                VendorType.GCP, "europe-west1"));
    }

    public String resolve(String logicalRegion, VendorType vendor) {
        Map<VendorType, String> mapping = REGIONS.get(normalize(logicalRegion));
        if (mapping == null) {
            throw new IllegalArgumentException(
                    "지원하지 않는 region 입니다: " + logicalRegion + " (지원: " + REGIONS.keySet() + ")");
        }
        String code = mapping.get(vendor);
        if (code == null) {
            throw new IllegalArgumentException(
                    vendor + "는 region " + logicalRegion + "를 지원하지 않습니다.");
        }
        return code;
    }

    public boolean isSupported(String logicalRegion) {
        return REGIONS.containsKey(normalize(logicalRegion));
    }

    public Set<String> logicalRegions() {
        return REGIONS.keySet();
    }

    public List<String> vendorCodes(VendorType vendor) {
        return REGIONS.values().stream()
                .map(m -> m.get(vendor))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private String normalize(String logicalRegion) {
        return logicalRegion == null ? "" : logicalRegion.trim().toLowerCase();
    }
}
