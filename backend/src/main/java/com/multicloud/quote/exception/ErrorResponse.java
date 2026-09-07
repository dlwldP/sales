package com.multicloud.quote.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(OffsetDateTime.now(), status, error, message, path);
    }
}
