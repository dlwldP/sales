package com.multicloud.quote.controller;

import com.multicloud.quote.dto.response.PriceSnapshotResponse;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.service.PriceService;
import com.multicloud.quote.service.PriceSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/prices")
@Tag(name = "Price", description = "캐시된 벤더 가격 조회 및 수동 동기화 (관리/디버그용)")
public class PriceController {

    private final PriceService priceService;
    private final PriceSyncService priceSyncService;

    public PriceController(PriceService priceService, PriceSyncService priceSyncService) {
        this.priceService = priceService;
        this.priceSyncService = priceSyncService;
    }

    @Operation(summary = "캐시 가격 조회",
            description = "PriceSnapshot 캐시를 조회한다. region은 논리 키(korea) 또는 벤더 코드(ap-northeast-2) 모두 허용.")
    @GetMapping
    public List<PriceSnapshotResponse> search(
            @RequestParam(required = false) VendorType vendor,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) OsType os,
            @RequestParam(required = false) Integer vcpu,
            @RequestParam(required = false) Integer memoryGb) {
        return priceService.search(vendor, region, os, vcpu, memoryGb).stream()
                .map(PriceSnapshotResponse::from)
                .toList();
    }

    @Operation(summary = "가격 수동 동기화",
            description = "스케줄러를 기다리지 않고 즉시 외부 가격 API를 폴링해 캐시를 갱신한다.")
    @PostMapping("/sync")
    public PriceSyncService.SyncReport sync() {
        return priceSyncService.syncAll();
    }
}
