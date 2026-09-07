package com.multicloud.quote.service;

import com.multicloud.quote.config.RegionCatalog;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.repository.PriceSnapshotRepository;
import com.multicloud.quote.service.vendor.InstancePrice;
import com.multicloud.quote.service.vendor.VendorPriceClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 벤더/리전/OS 한 조합의 가격을 수집해 PriceSnapshot에 upsert 한다.
 * 조합 단위로 트랜잭션을 끊기 위해 PriceSyncService와 별도 빈으로 분리했다(자기호출 시 @Transactional 미적용).
 */
@Component
public class PriceCacheWriter {

    private final PriceSnapshotRepository snapshotRepository;
    private final VendorService vendorService;
    private final RegionCatalog regionCatalog;

    public PriceCacheWriter(PriceSnapshotRepository snapshotRepository,
                            VendorService vendorService,
                            RegionCatalog regionCatalog) {
        this.snapshotRepository = snapshotRepository;
        this.vendorService = vendorService;
        this.regionCatalog = regionCatalog;
    }

    @Transactional
    public int sync(VendorPriceClient client, String logicalRegion, OsType os) {
        String vendorRegionCode = regionCatalog.resolve(logicalRegion, client.vendor());
        List<InstancePrice> prices = client.fetchComputePrices(vendorRegionCode, os);
        if (prices.isEmpty()) {
            return 0;
        }

        Vendor vendor = vendorService.getOrCreate(client.vendor());
        Instant syncedAt = Instant.now();
        for (InstancePrice price : prices) {
            upsert(vendor, price, syncedAt);
        }
        return prices.size();
    }

    private void upsert(Vendor vendor, InstancePrice price, Instant syncedAt) {
        BigDecimal monthly = CostCalculator.toMonthly(price.hourlyCostUsd());
        snapshotRepository.findByVendorAndSkuAndRegionAndOs(vendor, price.sku(), price.regionCode(), price.os())
                .ifPresentOrElse(
                        existing -> existing.refresh(price.hourlyCostUsd(), monthly,
                                price.vcpu(), price.memoryGb(), syncedAt),
                        () -> snapshotRepository.save(PriceSnapshot.builder()
                                .vendor(vendor)
                                .sku(price.sku())
                                .vcpu(price.vcpu())
                                .memoryGb(price.memoryGb())
                                .region(price.regionCode())
                                .os(price.os())
                                .hourlyCostUsd(price.hourlyCostUsd())
                                .monthlyCostUsd(monthly)
                                .syncedAt(syncedAt)
                                .build()));
    }
}
