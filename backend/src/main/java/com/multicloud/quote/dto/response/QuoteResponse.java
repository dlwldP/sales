package com.multicloud.quote.dto.response;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.QuoteRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Schema(description = "견적 상세 응답")
public record QuoteResponse(
        Long quoteId,
        String workloadName,
        Integer vcpu,
        Integer memoryGb,
        Integer storageGb,
        String region,
        OsType os,
        OffsetDateTime createdAt,
        List<QuoteItemResponse> results
) {

    public static QuoteResponse from(QuoteRequest quote) {
        List<QuoteItemResponse> results = quote.getItems().stream()
                .map(QuoteItemResponse::from)
                .sorted(Comparator.comparing(QuoteItemResponse::monthlyCostUsd,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return new QuoteResponse(
                quote.getId(),
                quote.getWorkloadName(),
                quote.getVcpu(),
                quote.getMemoryGb(),
                quote.getStorageGb(),
                quote.getRegion(),
                quote.getOs(),
                quote.getCreatedAt(),
                results
        );
    }
}
