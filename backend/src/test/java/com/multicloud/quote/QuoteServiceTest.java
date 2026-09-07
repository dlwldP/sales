package com.multicloud.quote;

import com.multicloud.quote.dto.request.QuoteCreateRequest;
import com.multicloud.quote.dto.request.QuoteHistoryFilter;
import com.multicloud.quote.dto.response.QuoteResponse;
import com.multicloud.quote.entity.OsType;
import com.multicloud.quote.entity.PriceSnapshot;
import com.multicloud.quote.entity.Vendor;
import com.multicloud.quote.entity.VendorType;
import com.multicloud.quote.exception.QuoteNotFoundException;
import com.multicloud.quote.repository.PriceSnapshotRepository;
import com.multicloud.quote.service.QuoteService;
import com.multicloud.quote.service.VendorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class QuoteServiceTest {

    @Autowired
    private QuoteService quoteService;
    @Autowired
    private VendorService vendorService;
    @Autowired
    private PriceSnapshotRepository snapshotRepository;

    @BeforeEach
    void seedPriceCache() {
        snapshotRepository.deleteAll();
        Vendor aws = vendorService.getOrCreate(VendorType.AWS);
        Vendor azure = vendorService.getOrCreate(VendorType.AZURE);

        // 스펙 미달(2vCPU) — 매칭 후보에서 제외되어야 한다
        save(aws, "t3.large", 2, 8, "ap-northeast-2", new BigDecimal("0.104"));
        // 스펙 충족 + 최저가
        save(aws, "t3.xlarge", 4, 16, "ap-northeast-2", new BigDecimal("0.208"));
        // 스펙 충족이지만 더 비싼 상위 SKU
        save(aws, "m5.2xlarge", 8, 32, "ap-northeast-2", new BigDecimal("0.472"));
        save(azure, "Standard_D4s_v5", 4, 16, "koreacentral", new BigDecimal("0.192"));
    }

    private void save(Vendor vendor, String sku, int vcpu, int memoryGb, String region, BigDecimal hourly) {
        snapshotRepository.save(PriceSnapshot.builder()
                .vendor(vendor)
                .sku(sku)
                .vcpu(vcpu)
                .memoryGb(memoryGb)
                .region(region)
                .os(OsType.LINUX)
                .hourlyCostUsd(hourly)
                .monthlyCostUsd(hourly.multiply(BigDecimal.valueOf(730)))
                .syncedAt(Instant.now())
                .build());
    }

    @Test
    @DisplayName("요청 스펙을 충족하는 최저가 SKU로 벤더별 견적을 산출한다")
    void createsQuoteWithCheapestMatchingSku() {
        QuoteResponse response = quoteService.create(request(4, 16, 100, List.of(VendorType.AWS, VendorType.AZURE)));

        assertThat(response.quoteId()).isNotNull();
        assertThat(response.results()).hasSize(2);

        var aws = response.results().stream()
                .filter(r -> r.vendor() == VendorType.AWS).findFirst().orElseThrow();
        assertThat(aws.matchedSku()).isEqualTo("t3.xlarge");
        // 0.208 × 730h = 151.84, 스토리지 100GB × 0.0912 = 9.12
        assertThat(aws.computeCostUsd()).isEqualByComparingTo("151.84");
        assertThat(aws.storageCostUsd()).isEqualByComparingTo("9.12");
        assertThat(aws.monthlyCostUsd()).isEqualByComparingTo("160.96");
    }

    @Test
    @DisplayName("견적 결과는 월 비용 오름차순으로 정렬된다")
    void sortsResultsByMonthlyCost() {
        QuoteResponse response = quoteService.create(request(4, 16, 0, List.of(VendorType.AWS, VendorType.AZURE)));

        assertThat(response.results())
                .extracting(r -> r.vendor())
                .containsExactly(VendorType.AZURE, VendorType.AWS);
    }

    @Test
    @DisplayName("매칭되는 가격 데이터가 없으면 사유를 담은 항목을 반환한다")
    void returnsNoteWhenNoPriceMatches() {
        QuoteResponse response = quoteService.create(request(256, 1024, 100, List.of(VendorType.AWS)));

        assertThat(response.results()).singleElement().satisfies(item -> {
            assertThat(item.matchedSku()).isNull();
            assertThat(item.monthlyCostUsd()).isNull();
            assertThat(item.note()).isNotBlank();
        });
    }

    @Test
    @DisplayName("지원하지 않는 리전은 400으로 이어지는 IllegalArgumentException을 던진다")
    void rejectsUnsupportedRegion() {
        QuoteCreateRequest request = new QuoteCreateRequest(
                "테스트", 4, 16, 100, "mars", OsType.LINUX, List.of(VendorType.AWS));

        assertThatThrownBy(() -> quoteService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }

    @Test
    @DisplayName("저장된 견적을 단건 조회하고, 없는 ID는 예외를 던진다")
    void findsQuoteById() {
        QuoteResponse created = quoteService.create(request(4, 16, 50, List.of(VendorType.AZURE)));

        assertThat(quoteService.findById(created.quoteId()).workloadName()).isEqualTo("테스트 서버");
        assertThatThrownBy(() -> quoteService.findById(999_999L))
                .isInstanceOf(QuoteNotFoundException.class);
    }

    @Test
    @DisplayName("견적 이력은 최신순 페이지로 조회된다")
    void listsQuoteHistory() {
        quoteService.create(request(4, 16, 10, List.of(VendorType.AWS)));
        quoteService.create(request(4, 16, 20, List.of(VendorType.AWS)));

        var page = quoteService.findAll(QuoteHistoryFilter.empty(), 0, 10);
        assertThat(page.content()).isNotEmpty();
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.page()).isZero();
    }

    @Test
    @DisplayName("리전 필터는 해당 리전 견적만 남긴다")
    void filtersHistoryByRegion() {
        quoteService.create(request(4, 16, 10, List.of(VendorType.AWS)));

        var korea = quoteService.findAll(
                new QuoteHistoryFilter("korea", null, null, null, null), 0, 10);
        var tokyo = quoteService.findAll(
                new QuoteHistoryFilter("tokyo", null, null, null, null), 0, 10);

        assertThat(korea.content()).isNotEmpty();
        assertThat(korea.content()).allSatisfy(q -> assertThat(q.region()).isEqualTo("korea"));
        assertThat(tokyo.content()).isEmpty();
    }

    @Test
    @DisplayName("벤더 필터는 그 벤더가 포함된 견적만 남기고 중복 행을 만들지 않는다")
    void filtersHistoryByVendor() {
        quoteService.create(request(4, 16, 10, List.of(VendorType.AWS, VendorType.AZURE)));
        quoteService.create(request(4, 16, 10, List.of(VendorType.AZURE)));

        var aws = quoteService.findAll(
                new QuoteHistoryFilter(null, VendorType.AWS, null, null, null), 0, 10);
        var gcp = quoteService.findAll(
                new QuoteHistoryFilter(null, VendorType.GCP, null, null, null), 0, 10);

        // AWS를 포함한 견적은 1건이며, 항목이 2개라도 행이 중복되지 않아야 한다
        assertThat(aws.totalElements()).isEqualTo(1);
        assertThat(aws.content()).hasSize(1);
        assertThat(gcp.content()).isEmpty();
    }

    @Test
    @DisplayName("기간 필터는 종료일 당일을 포함한다")
    void filtersHistoryByDateRange() {
        quoteService.create(request(4, 16, 10, List.of(VendorType.AWS)));
        LocalDate today = LocalDate.now();

        var included = quoteService.findAll(
                new QuoteHistoryFilter(null, null, null, today, today), 0, 10);
        var past = quoteService.findAll(
                new QuoteHistoryFilter(null, null, null, today.minusDays(10), today.minusDays(5)), 0, 10);

        assertThat(included.content()).isNotEmpty();
        assertThat(past.content()).isEmpty();
    }

    @Test
    @DisplayName("from이 to보다 이후면 예외를 던지고, 미지원 리전 필터도 거부한다")
    void rejectsInvalidFilters() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> new QuoteHistoryFilter(null, null, null, today, today.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from");

        assertThatThrownBy(() -> quoteService.findAll(
                new QuoteHistoryFilter("mars", null, null, null, null), 0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }

    private QuoteCreateRequest request(int vcpu, int memoryGb, int storageGb, List<VendorType> vendors) {
        return new QuoteCreateRequest("테스트 서버", vcpu, memoryGb, storageGb, "korea", OsType.LINUX, vendors);
    }
}
