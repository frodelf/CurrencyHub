package com.example.currencyhub.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
@Data
public class AppProperties {

    private Scheduler scheduler = new Scheduler();
    private ExternalApi externalApi = new ExternalApi();
    private Backfill backfill = new Backfill();
    private Redis redis = new Redis();
    private Providers providers = new Providers();

    @Data
    public static class Scheduler {
        private String refreshRate;
    }

    @Data
    public static class ExternalApi {
        @Min(value = 1, message = "{app.external-api.timeout-seconds.min}")
        private int timeoutSeconds;
        @Min(value = 1, message = "{app.external-api.concurrency-limit.min}")
        private int concurrencyLimit;
        @Min(value = 1, message = "{app.external-api.retry-attempts.min}")
        private int retryAttempts;
        @Min(value = 1, message = "{app.external-api.retry-initial-delay-ms.min}")
        private long retryInitialDelayMs;
        @Min(value = 1, message = "{app.external-api.retry-max-delay-ms.min}")
        private long retryMaxDelayMs;
    }

    @Data
    public static class Backfill {
        @Min(value = 1, message = "{app.backfill.default-concurrency-limit.min}")
        private int defaultConcurrencyLimit;
        @Min(value = 1, message = "{app.backfill.max-concurrency-limit.min}")
        private int maxConcurrencyLimit;
    }

    @Data
    public static class Redis {
        @Min(value = 1, message = "{app.redis.current-rates-ttl-minutes.min}")
        private int currentRatesTtlMinutes;
    }

    @Data
    public static class Providers {
        private ProviderConfig nbu = new ProviderConfig();
        private ProviderConfig privatbank = new ProviderConfig();
        private ProviderConfig monobank = new ProviderConfig();
        private ProviderConfig frankfurter = new ProviderConfig();
        private ProviderConfig openExchangeRates = new ProviderConfig();
        private ProviderConfig ecb = new ProviderConfig();
    }

    @Data
    public static class ProviderConfig {
        private String baseUrl;
        private String apiKey;
        private boolean enabled;
    }
}
