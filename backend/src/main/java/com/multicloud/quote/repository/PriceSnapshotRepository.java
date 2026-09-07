package com.multicloud.quote.repository;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findByVendorAndSkuAndRegionAndOs(Vendor vendor, String sku, String region, OsType os);

    /**
     * 요청 스펙을 충족(vcpu/메모리가 요청치 이상)하는 SKU 중 월 비용이 가장 낮은 순으로 조회.
     * 최소 사양을 만족하는 가장 저렴한 인스턴스를 고르는 것이 견적 매칭 규칙이다.
     */
    @Query("""
            select p from PriceSnapshot p
            join fetch p.vendor v
            where v.name = :vendor
              and p.region = :region
              and p.os = :os
              and p.vcpu >= :vcpu
              and p.memoryGb >= :memoryGb
            order by p.monthlyCostUsd asc, p.vcpu asc, p.memoryGb asc
            """)
    List<PriceSnapshot> findCheapestMatching(@Param("vendor") VendorType vendor,
                                             @Param("region") String region,
                                             @Param("os") OsType os,
                                             @Param("vcpu") int vcpu,
                                             @Param("memoryGb") int memoryGb,
                                             Pageable pageable);

    /** 관리/디버그용 캐시 조회. null 파라미터는 필터를 적용하지 않는다. */
    @Query("""
            select p from PriceSnapshot p
            join fetch p.vendor v
            where (:vendor is null or v.name = :vendor)
              and (:region is null or p.region = :region)
              and (:os is null or p.os = :os)
              and (:vcpu is null or p.vcpu = :vcpu)
              and (:memoryGb is null or p.memoryGb = :memoryGb)
            order by p.monthlyCostUsd asc
            """)
    List<PriceSnapshot> search(@Param("vendor") VendorType vendor,
                               @Param("region") String region,
                               @Param("os") OsType os,
                               @Param("vcpu") Integer vcpu,
                               @Param("memoryGb") Integer memoryGb,
                               Pageable pageable);

    long countByVendor(Vendor vendor);
}
