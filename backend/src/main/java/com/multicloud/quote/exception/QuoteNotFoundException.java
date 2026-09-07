package com.multicloud.quote.exception;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(Long quoteId) {
        super("견적을 찾을 수 없습니다. quoteId=" + quoteId);
    }
}
