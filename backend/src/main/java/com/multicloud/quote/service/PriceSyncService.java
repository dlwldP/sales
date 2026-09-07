package com.multicloud.quote.service;

import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.service.vendor.VendorPriceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 가격 API → PriceSnapshot 캐시 동기화 오케스트레이션.
 * 벤더/리전/OS 조합 하나가 실패해도 나머지 조합은 계속 진행한다.
 */
@Service
public class PriceSyncService {

    private static final Logger log = LoggerFactory.getLogger(PriceSyncService.class);

    private final List<VendorPriceClient> priceClients;
    private final PriceCacheWriter cacheWriter;
    private final AppProperties properties;

    public PriceSyncService(List<VendorPriceClient> priceClients,
                            PriceCacheWriter cacheWriter,
                            AppProperties properties) {
        this.priceClients = priceClients;
        this.cacheWriter = cacheWriter;
        this.properties = properties;
    }

    public SyncReport syncAll() {
        List<SyncOutcome> outcomes = new ArrayList<>();

        for (VendorPriceClient client : priceClients) {
            if (!client.isEnabled()) {
                log.info("{} 벤더는 비활성 상태라 동기화를 건너뜁니다.", client.vendor());
                continue;
            }
            for (String logicalRegion : properties.priceSync().regions()) {
                for (OsType os : properties.priceSync().osTypes()) {
                    outcomes.add(syncSafely(client, logicalRegion, os));
                }
            }
        }

        SyncReport report = new SyncReport(Instant.now(), outcomes);
        log.info("가격 동기화 완료: 성공 {}건 / 실패 {}건, 총 {}개 SKU",
                report.successCount(), report.failureCount(), report.totalSyncedSkus());
        return report;
    }

    private SyncOutcome syncSafely(VendorPriceClient client, String logicalRegion, OsType os) {
        try {
            int count = cacheWriter.sync(client, logicalRegion, os);
            return SyncOutcome.success(client.vendor(), logicalRegion, os, count);
        } catch (Exception e) {
            log.warn("가격 동기화 실패: vendor={}, region={}, os={}, reason={}",
                    client.vendor(), logicalRegion, os, e.getMessage());
            return SyncOutcome.failure(client.vendor(), logicalRegion, os, e.getMessage());
        }
    }

    public record SyncOutcome(VendorType vendor, String region, OsType os, int syncedSkus,
                              boolean success, String errorMessage) {

        static SyncOutcome success(VendorType vendor, String region, OsType os, int syncedSkus) {
            return new SyncOutcome(vendor, region, os, syncedSkus, true, null);
        }

        static SyncOutcome failure(VendorType vendor, String region, OsType os, String errorMessage) {
            return new SyncOutcome(vendor, region, os, 0, false, errorMessage);
        }
    }

    public record SyncReport(Instant finishedAt, List<SyncOutcome> outcomes) {

        public long successCount() {
            return outcomes.stream().filter(SyncOutcome::success).count();
        }

        public long failureCount() {
            return outcomes.stream().filter(o -> !o.success()).count();
        }

        public int totalSyncedSkus() {
            return outcomes.stream().mapToInt(SyncOutcome::syncedSkus).sum();
        }
    }
}
