package com.multicloud.quote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 스케줄러가 벤더 공개 가격 API에서 수집한 인스턴스 단가 스냅샷.
 * 내부 API는 외부 API를 직접 호출하지 않고 이 테이블을 서빙한다.
 */
@Entity
@Table(
        name = "price_snapshot",
        indexes = {
                @Index(name = "idx_price_lookup", columnList = "vendor_id,region,vcpu,memory_gb"),
                @Index(name = "idx_price_synced_at", columnList = "synced_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_price_snapshot_sku",
                columnNames = {"vendor_id", "sku", "region", "os"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false)
    private Integer vcpu;

    @Column(name = "memory_gb", nullable = false)
    private Integer memoryGb;

    @Column(nullable = false, length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OsType os;

    /** 시간당 온디맨드 단가(USD). */
    @Column(name = "hourly_cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal hourlyCostUsd;

    /** 730시간(월 평균) 기준 환산 월 비용(USD). */
    @Column(name = "monthly_cost_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyCostUsd;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Builder
    public PriceSnapshot(Vendor vendor, String sku, Integer vcpu, Integer memoryGb, String region,
                         OsType os, BigDecimal hourlyCostUsd, BigDecimal monthlyCostUsd, Instant syncedAt) {
        this.vendor = vendor;
        this.sku = sku;
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.region = region;
        this.os = os;
        this.hourlyCostUsd = hourlyCostUsd;
        this.monthlyCostUsd = monthlyCostUsd;
        this.syncedAt = syncedAt;
    }

    public void refresh(BigDecimal hourlyCostUsd, BigDecimal monthlyCostUsd, Integer vcpu,
                        Integer memoryGb, Instant syncedAt) {
        this.hourlyCostUsd = hourlyCostUsd;
        this.monthlyCostUsd = monthlyCostUsd;
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.syncedAt = syncedAt;
    }
}
