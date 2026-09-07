package com.multicloud.quote;

import com.multicloud.quote.service.vendor.AzureSkuSpecResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureSkuSpecResolverTest {

    private final AzureSkuSpecResolver resolver = new AzureSkuSpecResolver();

    @Test
    @DisplayName("범용 D 시리즈는 vCPU당 4GB로 해석한다")
    void resolvesGeneralPurposeSeries() {
        assertThat(resolver.resolve("Standard_D4s_v5"))
                .hasValueSatisfying(spec -> {
                    assertThat(spec.vcpu()).isEqualTo(4);
                    assertThat(spec.memoryGb()).isEqualTo(16);
                });
    }

    @Test
    @DisplayName("메모리 최적화 E 시리즈는 vCPU당 8GB로 해석한다")
    void resolvesMemoryOptimizedSeries() {
        assertThat(resolver.resolve("Standard_E8s_v5"))
                .hasValueSatisfying(spec -> {
                    assertThat(spec.vcpu()).isEqualTo(8);
                    assertThat(spec.memoryGb()).isEqualTo(64);
                });
    }

    @Test
    @DisplayName("제약 코어 SKU는 vCPU만 줄고 메모리는 기본 크기를 유지한다")
    void resolvesConstrainedCoreSku() {
        assertThat(resolver.resolve("Standard_E8-4s_v5"))
                .hasValueSatisfying(spec -> {
                    assertThat(spec.vcpu()).isEqualTo(4);
                    assertThat(spec.memoryGb()).isEqualTo(64);
                });
    }

    @Test
    @DisplayName("B 시리즈는 접미사(m)에 따라 메모리 비율이 달라진다")
    void resolvesBurstableSeries() {
        assertThat(resolver.resolve("Standard_B2ms"))
                .hasValueSatisfying(spec -> assertThat(spec.memoryGb()).isEqualTo(8));
        assertThat(resolver.resolve("Standard_B2s"))
                .hasValueSatisfying(spec -> assertThat(spec.memoryGb()).isEqualTo(4));
    }

    @Test
    @DisplayName("비율이 일정하지 않은 GPU/HPC 시리즈와 비정형 이름은 해석하지 않는다")
    void skipsUnsupportedSeries() {
        assertThat(resolver.resolve("Standard_NC6s_v3")).isEmpty();
        assertThat(resolver.resolve("Standard_M8ms")).isEmpty();
        assertThat(resolver.resolve("not-a-sku")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
    }
}
