package com.multicloud.quote.service.vendor;

import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.VendorType;

import java.util.List;

/** 벤더 공개 가격 API 어댑터. 스케줄러만 호출하며, 요청 경로에서는 호출하지 않는다. */
public interface VendorPriceClient {

    VendorType vendor();

    boolean isEnabled();

    /**
     * 지정 리전/OS의 컴퓨트 인스턴스 단가 목록을 조회한다.
     *
     * @param vendorRegionCode 벤더 리전 코드 (ap-northeast-2, koreacentral …)
     * @throws com.multicloud.quote.exception.PriceApiException 외부 API 호출/파싱 실패 시
     */
    List<InstancePrice> fetchComputePrices(String vendorRegionCode, OsType os);
}
