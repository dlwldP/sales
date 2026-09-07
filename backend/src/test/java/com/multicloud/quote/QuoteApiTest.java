package com.multicloud.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multicloud.quote.dto.request.QuoteCreateRequest;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.repository.PriceSnapshotRepository;
import com.multicloud.quote.service.VendorService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired
    private com.multicloud.quote.repository.QuoteRequestRepository quoteRequestRepository;

    @BeforeEach
    void seedPriceCache() {
        // 이 테스트 클래스는 @Transactional이 아니라 데이터가 메서드 간에 남으므로 매번 초기화한다.
        quoteRequestRepository.deleteAll();
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
    @DisplayName("GET /api/v1/quotes 는 region/vendor/기간으로 필터링된다")
    void filtersQuoteHistory() throws Exception {
        createQuote(List.of(VendorType.AWS, VendorType.AZURE));
        String today = LocalDate.now().toString();

        mockMvc.perform(get("/api/v1/quotes").param("region", "korea").param("vendor", "AWS")
                        .param("from", today).param("to", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].region").value("korea"));

        mockMvc.perform(get("/api/v1/quotes").param("vendor", "GCP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("잘못된 기간/리전 필터는 400을 반환한다")
    void rejectsInvalidFilter() throws Exception {
        mockMvc.perform(get("/api/v1/quotes")
                        .param("from", "2026-09-10").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(get("/api/v1/quotes").param("region", "mars"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("region")));
    }

    @Test
    @DisplayName("GET /api/v1/quotes/{id}/pdf 는 한글이 포함된 견적서 PDF를 반환한다")
    void exportsQuotePdf() throws Exception {
        long quoteId = createQuote(List.of(VendorType.AWS, VendorType.AZURE));

        byte[] pdf = mockMvc.perform(get("/api/v1/quotes/{id}/pdf", quoteId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("quote-" + quoteId)))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);

            assertThat(document.getNumberOfPages()).isEqualTo(1);
            // 한글이 깨지지 않고 텍스트로 추출되는지 확인 (CJK 폰트 인코딩 검증)
            assertThat(text).contains("멀티클라우드 견적서");
            assertThat(text).contains("워크로드 스펙");
            assertThat(text).contains("테스트 서버");
            // 견적 내용
            // AWS 0.208×730+9.12=160.96, AZURE 0.192×730+8.80=148.96
            assertThat(text).contains("t3.xlarge").contains("Standard_D4s_v5");
            assertThat(text).contains("$160.96").contains("$148.96");
            // 최저가 표시와 절감액 요약
            assertThat(text).contains("최저가");
            assertThat(text).contains("절감");
        }
    }

    @Test
    @DisplayName("없는 견적의 PDF 요청은 404를 반환한다")
    void exportPdfNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/quotes/{id}/pdf", 999_999))
                .andExpect(status().isNotFound());
    }

    private long createQuote(List<VendorType> vendors) throws Exception {
        String body = objectMapper.writeValueAsString(new QuoteCreateRequest(
                "테스트 서버", 4, 16, 100, "korea", OsType.LINUX, vendors));
        String response = mockMvc.perform(post("/api/v1/quotes")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("quoteId").asLong();
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
