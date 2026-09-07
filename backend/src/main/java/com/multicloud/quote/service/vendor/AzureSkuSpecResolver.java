package com.multicloud.quote.service.vendor;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Azure Retail Prices API 응답에는 vCPU/메모리 정보가 없다.
 * 견적 매칭에는 스펙이 필요하므로 armSkuName(Standard_D4s_v5 등)을 파싱해 스펙을 유추한다.
 *
 * <p>지원하는 범용/컴퓨트/메모리 최적화 시리즈만 해석하고, GPU(N)·HPC(H)·초대형 메모리(M) 등
 * vCPU당 메모리 비율이 일정하지 않은 시리즈는 매칭 대상에서 제외한다.
 */
@Component
public class AzureSkuSpecResolver {

    /** 예: Standard_D2s_v5, Standard_E8-4ds_v5, Standard_F4s_v2 */
    private static final Pattern SKU_PATTERN =
            Pattern.compile("^Standard_([A-Z]+)(\\d+)(?:-(\\d+))?([a-z]*)(?:_v(\\d+))?$");

    /** 시리즈별 vCPU당 메모리(GB). */
    private static final Map<String, Integer> MEMORY_PER_VCPU = Map.of(
            "A", 2,
            "D", 4,
            "DC", 4,
            "E", 8,
            "EC", 8,
            "F", 2,
            "L", 8
    );

    /** B 시리즈는 접미사에 따라 비율이 달라 별도 처리한다. */
    private static final String BURSTABLE_SERIES = "B";

    public Optional<InstanceSpec> resolve(String armSkuName) {
        if (armSkuName == null || armSkuName.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = SKU_PATTERN.matcher(armSkuName.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String series = matcher.group(1);
        int baseVcpu = Integer.parseInt(matcher.group(2));
        Integer constrainedVcpu = matcher.group(3) == null ? null : Integer.parseInt(matcher.group(3));
        String suffix = matcher.group(4) == null ? "" : matcher.group(4);

        Integer memoryPerVcpu = memoryPerVcpu(series, suffix);
        if (memoryPerVcpu == null || baseVcpu <= 0) {
            return Optional.empty();
        }

        // 제약 코어(Standard_E8-4s_v5)는 vCPU만 줄고 메모리는 기본 크기를 그대로 유지한다.
        int vcpu = constrainedVcpu != null ? constrainedVcpu : baseVcpu;
        int memoryGb = baseVcpu * memoryPerVcpu;
        if (vcpu <= 0) {
            return Optional.empty();
        }
        return Optional.of(new InstanceSpec(vcpu, memoryGb));
    }

    private Integer memoryPerVcpu(String series, String suffix) {
        if (BURSTABLE_SERIES.equals(series)) {
            // B2ms=4GB/vCPU, B2s=2GB/vCPU
            return suffix.contains("m") ? 4 : 2;
        }
        return MEMORY_PER_VCPU.get(series);
    }

    public record InstanceSpec(int vcpu, int memoryGb) {
    }
}
