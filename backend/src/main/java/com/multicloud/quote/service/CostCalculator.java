package com.multicloud.quote.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 시간 단가 → 월 비용 환산 등 견적 공통 계산. */
public final class CostCalculator {

    /** 월 환산 기준 시간 (24h × 365d ÷ 12개월 ≈ 730). */
    public static final BigDecimal MONTHLY_HOURS = BigDecimal.valueOf(730);

    private CostCalculator() {
    }

    public static BigDecimal toMonthly(BigDecimal hourlyCostUsd) {
        if (hourlyCostUsd == null) {
            return BigDecimal.ZERO;
        }
        return hourlyCostUsd.multiply(MONTHLY_HOURS).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal storageCost(BigDecimal ratePerGbMonth, int storageGb) {
        if (ratePerGbMonth == null || storageGb <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return ratePerGbMonth.multiply(BigDecimal.valueOf(storageGb)).setScale(2, RoundingMode.HALF_UP);
    }
}
