package com.multicloud.quote.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" "));
        if (message.isBlank()) {
            message = "입력값이 올바르지 않습니다.";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e,
                                                                    HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(" "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "입력값이 올바르지 않습니다." : message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                             HttpServletRequest request) {
        String message = "파라미터 '%s'의 값이 올바르지 않습니다: %s".formatted(e.getName(), e.getValue());
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "요청 본문을 해석할 수 없습니다. 필드 타입과 enum 값을 확인하세요.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(QuoteNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(PriceApiException.class)
    public ResponseEntity<ErrorResponse> handlePriceApi(PriceApiException e, HttpServletRequest request) {
        log.warn("외부 가격 API 처리 실패: {}", e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(status.value(), status.name(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
