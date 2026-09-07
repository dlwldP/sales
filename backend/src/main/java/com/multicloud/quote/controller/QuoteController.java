package com.multicloud.quote.controller;

import com.multicloud.quote.dto.request.QuoteCreateRequest;
import com.multicloud.quote.dto.response.PageResponse;
import com.multicloud.quote.dto.response.QuoteResponse;
import com.multicloud.quote.dto.response.QuoteSummaryResponse;
import com.multicloud.quote.exception.ErrorResponse;
import com.multicloud.quote.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/quotes")
@Validated
@Tag(name = "Quote", description = "멀티클라우드 견적 생성/조회")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @Operation(summary = "견적 생성", description = "워크로드 스펙으로 벤더별 월 비용을 산출하고 이력으로 저장한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<QuoteResponse> create(@Valid @RequestBody QuoteCreateRequest request) {
        QuoteResponse response = quoteService.create(request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/quotes/{id}")
                        .buildAndExpand(response.quoteId()).toUri())
                .body(response);
    }

    @Operation(summary = "견적 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "견적 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{quoteId}")
    public QuoteResponse findById(@PathVariable Long quoteId) {
        return quoteService.findById(quoteId);
    }

    @Operation(summary = "견적 이력 목록", description = "생성일 내림차순 페이지 조회")
    @GetMapping
    public PageResponse<QuoteSummaryResponse> findAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
        return quoteService.findAll(page, size);
    }
}
