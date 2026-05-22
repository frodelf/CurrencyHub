package com.example.currencyhub.logic.rate.service;

import com.example.currencyhub.logic.job.model.response.JobStartedResponse;
import com.example.currencyhub.logic.provider.model.response.ProviderResultResponse;
import com.example.currencyhub.logic.provider.service.RatesFetchService;
import com.example.currencyhub.logic.rate.entity.ExchangeRate;
import com.example.currencyhub.logic.rate.mapper.RateMapper;
import com.example.currencyhub.logic.rate.model.response.CurrentRateResponse;
import com.example.currencyhub.logic.rate.model.response.HistoryRateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateService {

    private final RateCacheService rateCacheService;
    private final RateStorageService rateStorageService;
    private final RatesFetchService ratesFetchService;
    private final RateMapper rateMapper;

    public List<CurrentRateResponse> getCurrentRates(String source, String baseCurrency, String currency) {
        return rateCacheService.getAllRates(source, baseCurrency, currency)
                .stream()
                .map(rateMapper::toCurrentRateResponse)
                .toList();
    }

    public Optional<CurrentRateResponse> getCurrentRate(String source, String baseCurrency, String currency) {
        return rateCacheService.getRate(source.toUpperCase(), baseCurrency.toUpperCase(), currency.toUpperCase())
                .map(rateMapper::toCurrentRateResponse);
    }

    public Page<HistoryRateResponse> getHistory(String source, String baseCurrency, String currency,
                                                 LocalDate from, LocalDate to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fetchedAt").descending());
        Page<ExchangeRate> historyPage = rateStorageService.getHistory(
                source,
                baseCurrency.toUpperCase(),
                currency.toUpperCase(),
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                pageable);
        return historyPage.map(rateMapper::toHistoryRateResponse);
    }

    public JobStartedResponse triggerRefresh(List<String> sources) {
        String jobId = "refresh-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Thread.ofVirtual().name("manual-refresh-" + jobId).start(() -> {
            log.info("[{}] Manual refresh started", jobId);
            List<ProviderResultResponse> results = ratesFetchService.fetchAndStore(jobId, sources);
            long ok = results.stream().filter(ProviderResultResponse::isSuccess).count();
            log.info("[{}] Manual refresh completed. providers={}/{}", jobId, ok, results.size());
        });
        return new JobStartedResponse(jobId, "STARTED");
    }
}
