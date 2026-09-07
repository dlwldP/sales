package com.multicloud.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multicloud.quote.dto.request.QuoteCreateRequest;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.repository.PriceSnapshotRepository;
import com.multicloud.quote.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private VendorService vendorService;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;

    @BeforeEach
    void seedPriceCache() {
        snapshotRepository.deleteAll();
        Vendor aws = vendorService.getOrCreate(VendorType.AWS);
        Vendor azure = vendorService.getOrCreate(VendorType.AZURE);
        save(aws, "t3.xlarge", 4, 16, "ap-northeast-2", new BigDecimal("0.208"));
        save(azure, "Standard_D4s_v5", 4, 16, "koreacentral", new BigDecimal("0.192"));
    }

    private void save(Vendor vendor, String sku, int vcpu, int memoryGb, String region, BigDecimal hourly) {
        snapshotRepository.save(PriceSnapshot.builder()
                .vendor(vendor).sku(sku).vcpu(vcpu).memoryGb(memoryGb).region(region).os(OsType.LINUX)
                .hourlyCostUsd(hourly)
                .monthlyCostUsd(hourly.multiply(BigDecimal.valueOf(730)))
                .syncedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("POST /api/v1/quotes 는 201과 벤더별 결과를 반환한다")
    void createQuote() throws Exception {
        String body = objectMapper.writeValueAsString(new QuoteCreateRequest(
                "테스트 서버", 4, 16, 100, "korea", OsType.LINUX, List.of(VendorType.AWS, VendorType.AZURE)));

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.quoteId").isNumber())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].vendor").value("AZURE"))
                .andExpect(jsonPath("$.results[0].matchedSku").value("Standard_D4s_v5"));
    }

    @Test
    @DisplayName("검증 실패는 공통 에러 포맷으로 400을 반환한다")
    void validationFailureReturnsErrorFormat() throws Exception {
        String body = objectMapper.writeValueAsString(new QuoteCreateRequest(
                "테스트 서버", 0, 16, 100, "korea", OsType.LINUX, List.of(VendorType.AWS)));

        mockMvc.perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("vcpu는 1 이상이어야 합니다."))
                .andExpect(jsonPath("$.path").value("/api/v1/quotes"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("없는 견적 ID는 404를 반환한다")
    void notFoundQuote() throws Exception {
        mockMvc.perform(get("/api/v1/quotes/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/quotes 는 페이지 응답을 반환한다")
    void listQuotes() throws Exception {
        mockMvc.perform(get("/api/v1/quotes").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/prices 는 캐시된 스냅샷을 반환한다")
    void searchPrices() throws Exception {
        mockMvc.perform(get("/api/v1/prices")
                        .param("vendor", "AWS").param("region", "korea")
                        .param("vcpu", "4").param("memoryGb", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("t3.xlarge"))
                .andExpect(jsonPath("$[0].monthlyCostUsd").value(151.84))
                .andExpect(jsonPath("$[0].syncedAt").exists());
    }
}
