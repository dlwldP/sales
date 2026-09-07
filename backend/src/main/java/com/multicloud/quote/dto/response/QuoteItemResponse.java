package com.multicloud.quote.dto.response;

import com.multicloud.quote.entity.QuoteItem;
import com.multicloud.quote.entity.VendorType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "벤더별 견적 산출 결과")
public record QuoteItemResponse(
        VendorType vendor,
        @Schema(example = "t3.xlarge") String matchedSku,
        BigDecimal computeCostUsd,
        BigDecimal storageCostUsd,
        @Schema(example = "121.4") BigDecimal monthlyCostUsd,
        @Schema(description = "매칭 실패 등 사유. 정상 산출 시 null") String note
) {

    public static QuoteItemResponse from(QuoteItem item) {
        return new QuoteItemResponse(
                item.getVendor().getName(),
                item.getMatchedSku(),
                item.getComputeCostUsd(),
                item.getStorageCostUsd(),
                item.getMonthlyCostUsd(),
                item.getNote()
        );
    }
}
