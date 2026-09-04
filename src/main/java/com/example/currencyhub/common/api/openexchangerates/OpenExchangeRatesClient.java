package com.example.currencyhub.common.api.openexchangerates;

import com.example.currencyhub.common.api.BackfillCapableProvider;
import com.example.currencyhub.common.api.ProviderHttpExecutor;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.config.AppProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenExchangeRatesClient implements BackfillCapableProvider {

    private static final String SOURCE = "OPEN_EXCHANGE_RATES";

    private final AppProperties properties;
    private final ProviderHttpExecutor httpExecutor;
    private final ObjectMapper objectMapper;

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public boolean supportsCurrentRates() {
        return true;
    }

    @Override
    public boolean supportsHistoricalRates() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        AppProperties.ProviderConfig cfg = properties.getProviders().getOpenExchangeRates();
        return cfg.isEnabled() && cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    }

    @Override
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getOpenExchangeRates().getBaseUrl() + "/latest.json";
        return fetchFromUrl(url, Instant.now());
    }

    @Override
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        String url = properties.getProviders().getOpenExchangeRates().getBaseUrl() + "/historical/" + date + ".json";
        return fetchFromUrl(url, date.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @SuppressWarnings("unchecked")
    private List<ExchangeRateDto> fetchFromUrl(String url, Instant publishedAt) throws Exception {
        String apiKey = properties.getProviders().getOpenExchangeRates().getApiKey();
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of("Authorization", "Token " + apiKey));

        Map<String, Object> rawMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
        String base = (String) rawMap.get("base");
        Map<String, Object> rates = (Map<String, Object>) rawMap.get("rates");
        Instant fetchedAt = Instant.now();

        return rates.entrySet().stream()
                .map(entry -> new ExchangeRateDto(
                        SOURCE,
                        base,
                        entry.getKey(),
                        new BigDecimal(entry.getValue().toString()),
                        null,
                        null,
                        fetchedAt,
                        publishedAt,
                        Map.of("currency", entry.getKey(), "rate", entry.getValue(), "base", base)))
                .toList();
    }
}