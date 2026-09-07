package com.multicloud.quote.service;

import com.multicloud.quote.config.RegionCatalog;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.repository.PriceSnapshotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 캐시된 PriceSnapshot 조회 전용 서비스. 외부 API를 직접 호출하지 않는다. */
@Service
@Transactional(readOnly = true)
public class PriceService {

    private static final int MAX_SEARCH_RESULTS = 200;

    private final PriceSnapshotRepository snapshotRepository;
    private final RegionCatalog regionCatalog;

    public PriceService(PriceSnapshotRepository snapshotRepository, RegionCatalog regionCatalog) {
        this.snapshotRepository = snapshotRepository;
        this.regionCatalog = regionCatalog;
    }

    /**
     * 요청 스펙을 충족하는 가장 저렴한 SKU를 찾는다.
     *
     * @param logicalRegion 논리 리전 키 (korea 등)
     */
    public Optional<PriceSnapshot> findBestMatch(VendorType vendor, String logicalRegion, OsType os,
                                                 int vcpu, int memoryGb) {
        String vendorRegionCode = regionCatalog.resolve(logicalRegion, vendor);
        return snapshotRepository
                .findCheapestMatching(vendor, vendorRegionCode, os, vcpu, memoryGb, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    /**
     * 관리/디버그용 캐시 조회.
     *
     * @param region 벤더 리전 코드(ap-northeast-2) 또는 논리 키(korea) 모두 허용
     */
    public List<PriceSnapshot> search(VendorType vendor, String region, OsType os, Integer vcpu, Integer memoryGb) {
        String vendorRegionCode = normalizeRegion(vendor, region);
        return snapshotRepository.search(vendor, vendorRegionCode, os, vcpu, memoryGb,
                PageRequest.of(0, MAX_SEARCH_RESULTS));
    }

    private String normalizeRegion(VendorType vendor, String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        if (vendor != null && regionCatalog.isSupported(region)) {
            return regionCatalog.resolve(region, vendor);
        }
        return region;
    }
}
