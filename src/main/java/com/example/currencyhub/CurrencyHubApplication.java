package com.example.currencyhub;

import com.example.currencyhub.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
@EnableConfigurationProperties(AppProperties.class)
public class CurrencyHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CurrencyHubApplication.class, args);
    }
}
