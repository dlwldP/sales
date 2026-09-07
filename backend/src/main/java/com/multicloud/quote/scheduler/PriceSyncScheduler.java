package com.multicloud.quote.scheduler;

import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.service.PriceSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 1회 벤더 가격 API를 폴링해 PriceSnapshot 캐시를 갱신한다.
 * 외부 API를 요청 경로에서 직접 호출하지 않기 위한 설계이며,
 * 동기화가 실패해도 마지막 캐시로 견적 서비스는 계속 동작한다.
 */
@Component
public class PriceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceSyncScheduler.class);

    private final PriceSyncService priceSyncService;
    private final AppProperties properties;

    public PriceSyncScheduler(PriceSyncService priceSyncService, AppProperties properties) {
        this.priceSyncService = priceSyncService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.price-sync.cron}", zone = "${app.price-sync.zone}")
    public void syncDaily() {
        log.info("일간 가격 동기화 시작");
        priceSyncService.syncAll();
    }

    /** 로컬 개발 편의를 위한 기동 시 1회 동기화 (기본 비활성). */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!properties.priceSync().runOnStartup()) {
            return;
        }
        log.info("기동 시 가격 동기화 시작 (app.price-sync.run-on-startup=true)");
        priceSyncService.syncAll();
    }
}
