package com.multicloud.quote.dto.response;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.VendorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "캐시된 벤더 가격 스냅샷")
public record PriceSnapshotResponse(
        VendorType vendor,
        String sku,
        Integer vcpu,
        Integer memoryGb,
        String region,
        OsType os,
        BigDecimal hourlyCostUsd,
        BigDecimal monthlyCostUsd,
        Instant syncedAt
) {

    public static PriceSnapshotResponse from(PriceSnapshot snapshot) {
        return new PriceSnapshotResponse(
                snapshot.getVendor().getName(),
                snapshot.getSku(),
                snapshot.getVcpu(),
                snapshot.getMemoryGb(),
                snapshot.getRegion(),
                snapshot.getOs(),
                snapshot.getHourlyCostUsd(),
                snapshot.getMonthlyCostUsd(),
                snapshot.getSyncedAt()
        );
    }
}
