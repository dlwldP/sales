package com.multicloud.quote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 견적 1건에 대한 벤더별 산출 결과. */
@Entity
@Table(name = "quote_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_request_id", nullable = false)
    private QuoteRequest quoteRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    /** 스펙에 매칭된 인스턴스 SKU. 매칭 실패 시 null. */
    @Column(name = "matched_sku", length = 100)
    private String matchedSku;

    @Column(name = "compute_cost_usd", precision = 12, scale = 2)
    private BigDecimal computeCostUsd;

    @Column(name = "storage_cost_usd", precision = 12, scale = 2)
    private BigDecimal storageCostUsd;

    @Column(name = "monthly_cost_usd", precision = 12, scale = 2)
    private BigDecimal monthlyCostUsd;

    /** 매칭 실패/외부 API 미동기화 등 사유. 성공 시 null. */
    @Column(length = 255)
    private String note;

    @Builder
    public QuoteItem(Vendor vendor, String matchedSku, BigDecimal computeCostUsd,
                     BigDecimal storageCostUsd, BigDecimal monthlyCostUsd, String note) {
        this.vendor = vendor;
        this.matchedSku = matchedSku;
        this.computeCostUsd = computeCostUsd;
        this.storageCostUsd = storageCostUsd;
        this.monthlyCostUsd = monthlyCostUsd;
        this.note = note;
    }

    void assignTo(QuoteRequest quoteRequest) {
        this.quoteRequest = quoteRequest;
    }
}
