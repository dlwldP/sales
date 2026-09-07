package com.multicloud.quote.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.pricing.PricingClient;

@Configuration
public class AwsPricingConfig {

    /**
     * AWS Price List Query API 엔드포인트는 us-east-1 고정이며 SigV4 서명은 SDK가 처리한다.
     * 자격증명은 DefaultCredentialsProvider 체인(환경변수/프로파일/인스턴스 프로파일)에서 해석한다.
     */
    @Bean
    @Lazy
    @ConditionalOnProperty(prefix = "app.vendors.aws", name = "enabled", havingValue = "true", matchIfMissing = true)
    public PricingClient pricingClient(AppProperties properties) {
        return PricingClient.builder()
                .region(Region.of(properties.vendors().aws().regionCode()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
