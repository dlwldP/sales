package com.multicloud.quote.dto.request;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 견적 이력 조회 필터. 모든 항목은 선택이며 null이면 해당 조건을 적용하지 않는다.
 *
 * @param region 논리 리전 키 (korea 등)
 * @param vendor 해당 벤더가 포함된 견적만
 * @param from   생성일 시작(해당 일자 포함)
 * @param to     생성일 종료(해당 일자 포함)
 */
public record QuoteHistoryFilter(
        String region,
        VendorType vendor,
        OsType os,
        LocalDate from,
        LocalDate to
) {

    public static QuoteHistoryFilter empty() {
        return new QuoteHistoryFilter(null, null, null, null, null);
    }

    public QuoteHistoryFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from은 to보다 이후일 수 없습니다.");
        }
        region = (region == null || region.isBlank()) ? null : region.trim().toLowerCase();
    }

    /** 시작일 00:00부터 조회한다. */
    public OffsetDateTime fromDateTime() {
        return from == null ? null : from.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /** 종료일을 포함하기 위해 다음 날 00:00 미만으로 비교한다. */
    public OffsetDateTime toDateTimeExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    public boolean isEmpty() {
        return region == null && vendor == null && os == null && from == null && to == null;
    }
}
