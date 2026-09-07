package com.multicloud.quote.service;

import com.multicloud.quote.config.AppProperties;
import com.multicloud.quote.config.RegionCatalog;
import com.multicloud.quote.dto.request.QuoteCreateRequest;
import com.multicloud.quote.dto.response.QuoteResponse;
import com.multicloud.quote.dto.response.QuoteSummaryResponse;
import com.multicloud.quote.dto.response.PageResponse;
import com.multicloud.quote.entity.*;
import com.multicloud.quote.exception.QuoteNotFoundException;
import com.multicloud.quote.repository.QuoteRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    private final QuoteRequestRepository quoteRequestRepository;
    private final PriceService priceService;
    private final VendorService vendorService;
    private final RegionCatalog regionCatalog;
    private final AppProperties properties;

    public QuoteService(QuoteRequestRepository quoteRequestRepository,
                        PriceService priceService,
                        VendorService vendorService,
                        RegionCatalog regionCatalog,
                        AppProperties properties) {
        this.quoteRequestRepository = quoteRequestRepository;
        this.priceService = priceService;
        this.vendorService = vendorService;
        this.regionCatalog = regionCatalog;
        this.properties = properties;
    }

    @Transactional
    public QuoteResponse create(QuoteCreateRequest request) {
        if (!regionCatalog.isSupported(request.region())) {
            throw new IllegalArgumentException(
                    "지원하지 않는 region 입니다: " + request.region() + " (지원: " + regionCatalog.logicalRegions() + ")");
        }

        QuoteRequest quote = QuoteRequest.builder()
                .workloadName(request.workloadName())
                .vcpu(request.vcpu())
                .memoryGb(request.memoryGb())
                .storageGb(request.storageGb())
                .region(request.region().trim().toLowerCase())
                .os(request.os())
                .createdAt(OffsetDateTime.now())
                .build();

        // 동일 벤더가 중복 전달되어도 1회만 산출한다.
        for (VendorType vendorType : new LinkedHashSet<>(request.vendors())) {
            quote.addItem(buildItem(vendorType, quote));
        }

        QuoteRequest saved = quoteRequestRepository.save(quote);
        return QuoteResponse.from(saved);
    }

    private QuoteItem buildItem(VendorType vendorType, QuoteRequest quote) {
        Vendor vendor = vendorService.getOrCreate(vendorType);
        BigDecimal storageCost = CostCalculator.storageCost(
                properties.storage().rateFor(vendorType), quote.getStorageGb());

        Optional<PriceSnapshot> match = priceService.findBestMatch(
                vendorType, quote.getRegion(), quote.getOs(), quote.getVcpu(), quote.getMemoryGb());

        if (match.isEmpty()) {
            log.info("가격 캐시에서 매칭 SKU를 찾지 못했습니다. vendor={}, region={}, vcpu={}, memoryGb={}",
                    vendorType, quote.getRegion(), quote.getVcpu(), quote.getMemoryGb());
            return QuoteItem.builder()
                    .vendor(vendor)
                    .note("조건을 만족하는 가격 데이터가 없습니다. 가격 동기화 상태를 확인하세요.")
                    .build();
        }

        PriceSnapshot snapshot = match.get();
        BigDecimal computeCost = snapshot.getMonthlyCostUsd();
        return QuoteItem.builder()
                .vendor(vendor)
                .matchedSku(snapshot.getSku())
                .computeCostUsd(computeCost)
                .storageCostUsd(storageCost)
                .monthlyCostUsd(computeCost.add(storageCost))
                .build();
    }

    public QuoteResponse findById(Long quoteId) {
        QuoteRequest quote = quoteRequestRepository.findWithItemsById(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
        return QuoteResponse.from(quote);
    }

    public PageResponse<QuoteSummaryResponse> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return PageResponse.of(
                quoteRequestRepository.findAllByOrderByCreatedAtDesc(pageable),
                QuoteSummaryResponse::from);
    }
}
