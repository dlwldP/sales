package com.multicloud.quote.exception;

import com.multicloud.quote.entity.VendorType;

/** 외부 가격 API 호출 실패. 캐시 폴백도 불가능한 경우 502로 변환된다. */
public class PriceApiException extends RuntimeException {

    private final VendorType vendor;

    public PriceApiException(VendorType vendor, String message) {
        super("[" + vendor + "] " + message);
        this.vendor = vendor;
    }

    public PriceApiException(VendorType vendor, String message, Throwable cause) {
        super("[" + vendor + "] " + message, cause);
        this.vendor = vendor;
    }

    public VendorType getVendor() {
        return vendor;
    }
}
