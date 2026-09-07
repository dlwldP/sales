package com.multicloud.quote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quote_request", indexes = @Index(name = "idx_quote_created_at", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workload_name", nullable = false, length = 100)
    private String workloadName;

    @Column(nullable = false)
    private Integer vcpu;

    @Column(name = "memory_gb", nullable = false)
    private Integer memoryGb;

    @Column(name = "storage_gb", nullable = false)
    private Integer storageGb;

    @Column(nullable = false, length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OsType os;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "quoteRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuoteItem> items = new ArrayList<>();

    @Builder
    public QuoteRequest(String workloadName, Integer vcpu, Integer memoryGb, Integer storageGb,
                        String region, OsType os, OffsetDateTime createdAt) {
        this.workloadName = workloadName;
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.storageGb = storageGb;
        this.region = region;
        this.os = os;
        this.createdAt = createdAt;
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.assignTo(this);
    }
}
