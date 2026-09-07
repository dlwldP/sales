package com.multicloud.quote.dto.response;

import com.multicloud.quote.entity.QuoteRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "견적 이력 목록 항목")
public record QuoteSummaryResponse(
        Long quoteId,
        String workloadName,
        Integer vcpu,
        Integer memoryGb,
        Integer storageGb,
        String region,
        OffsetDateTime createdAt
) {

    public static QuoteSummaryResponse from(QuoteRequest quote) {
        return new QuoteSummaryResponse(
                quote.getId(),
                quote.getWorkloadName(),
                quote.getVcpu(),
                quote.getMemoryGb(),
                quote.getStorageGb(),
                quote.getRegion(),
                quote.getCreatedAt()
        );
    }
}
