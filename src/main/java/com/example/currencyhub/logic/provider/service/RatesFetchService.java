package com.example.currencyhub.logic.provider.service;

import com.example.currencyhub.common.api.ExchangeRateProvider;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.logic.provider.model.response.ProviderResultResponse;
import com.example.currencyhub.config.AppProperties;
import com.example.currencyhub.logic.rate.service.RateCacheService;
import com.example.currencyhub.logic.rate.service.RateStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
@RequiredArgsConstructor
public class RatesFetchService {

    private final List<ExchangeRateProvider> providers;
    private final RateCacheService rateCacheService;
    private final RateStorageService rateStorageService;
    private final AppProperties properties;

    public List<ProviderResultResponse> fetchAndStore(String jobId, List<String> sources) {
        List<ExchangeRateProvider> targets = resolveProviders(sources);
        if (targets.isEmpty()) {
            log.warn("[{}] No enabled providers matched sources={}", jobId, sources);
            return List.of();
        }

        Semaphore semaphore = new Semaphore(properties.getExternalApi().getConcurrencyLimit());
        List<ProviderResultResponse> results;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ProviderResultResponse>> futures = targets.stream()
                    .map(p -> executor.submit(() -> fetchFromProvider(p, semaphore, jobId)))
                    .toList();

            results = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    log.error("[{}] Unexpected error collecting provider future: {}", jobId, e.getMessage(), e);
                    return ProviderResultResponse.failure("UNKNOWN", e.getMessage());
                }
            }).toList();
        }

        results.stream()
                .filter(ProviderResultResponse::isSuccess)
                .forEach(r -> persist(r.getRates(), jobId, false));

        return results;
    }

    private ProviderResultResponse fetchFromProvider(ExchangeRateProvider provider, Semaphore semaphore, String jobId) {
        try {
            semaphore.acquire();
            try {
                List<ExchangeRateDto> rates = provider.fetchCurrentRates();
                log.info("[{}] Provider {} returned {} rates", jobId, provider.getSource(), rates.size());
                return ProviderResultResponse.success(provider.getSource(), rates);
            } finally {
                semaphore.release();
            }
        } catch (Exception e) {
            log.error("[{}] Provider {} failed: {}", jobId, provider.getSource(), e.getMessage());
            return ProviderResultResponse.failure(provider.getSource(), e.getMessage());
        }
    }

    private void persist(List<ExchangeRateDto> rates, String jobId, boolean isBackfill) {
        rateCacheService.saveRates(rates);
        rateStorageService.saveRates(rates, jobId, isBackfill);
    }

    private List<ExchangeRateProvider> resolveProviders(List<String> sources) {
        if (sources == null || sources.isEmpty()) {
            return providers.stream().filter(ExchangeRateProvider::isEnabled).toList();
        }
        Set<String> requested = new HashSet<>();
        sources.forEach(s -> requested.add(s.toUpperCase()));
        return providers.stream()
                .filter(ExchangeRateProvider::isEnabled)
                .filter(p -> requested.contains(p.getSource().toUpperCase()))
                .toList();
    }
}
