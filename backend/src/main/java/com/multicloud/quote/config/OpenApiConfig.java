package com.multicloud.quote.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quoteAutomationOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("멀티클라우드 견적 자동화 API")
                        .version("v1")
                        .description("""
                                AWS / Azure / GCP 공개 가격 API를 캐싱해 워크로드 스펙 기준 벤더별 견적을 산출한다.
                                외부 API는 스케줄러가 주기적으로 수집하며, 견적 API는 캐시(PriceSnapshot)를 서빙한다.
                                """))
                .servers(List.of(new Server().url("/").description("current")));
    }
}
