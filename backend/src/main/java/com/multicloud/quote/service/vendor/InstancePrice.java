package com.multicloud.quote.service.vendor;

import com.multicloud.quote.entity.OsType;

import java.math.BigDecimal;

/**
 * 벤더 가격 API에서 정규화한 인스턴스 단가 1건.
 *
 * @param sku            벤더 인스턴스 타입 (t3.xlarge, Standard_D4s_v5 …)
 * @param vcpu           vCPU 수
 * @param memoryGb       메모리(GB)
 * @param regionCode     벤더 리전 코드
 * @param hourlyCostUsd  시간당 온디맨드 단가(USD)
 */
public record InstancePrice(
        String sku,
        int vcpu,
        int memoryGb,
        String regionCode,
        OsType os,
        BigDecimal hourlyCostUsd
) {
}
