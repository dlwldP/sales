package com.multicloud.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuoteAutomationApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuoteAutomationApplication.class, args);
    }
}
