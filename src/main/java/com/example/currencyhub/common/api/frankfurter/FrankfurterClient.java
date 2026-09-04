package com.example.currencyhub.common.api.frankfurter;

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
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class FrankfurterClient implements BackfillCapableProvider {

    private static final String SOURCE = "FRANKFURTER";

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
        return properties.getProviders().getFrankfurter().isEnabled();
    }

    @Override
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        return fetchFromUrl(properties.getProviders().getFrankfurter().getBaseUrl() + "/latest");
    }

    @Override
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        return fetchFromUrl(properties.getProviders().getFrankfurter().getBaseUrl() + "/" + date);
    }

    @SuppressWarnings("unchecked")
    private List<ExchangeRateDto> fetchFromUrl(String url) throws Exception {
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of());

        Map<String, Object> rawMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
        String baseCurrency = (String) rawMap.get("base");
        String dateStr = (String) rawMap.get("date");
        Map<String, Object> rates = (Map<String, Object>) rawMap.get("rates");

        Instant fetchedAt = Instant.now();
        Instant publishedAt = parseDate(dateStr);

        return rates.entrySet().stream()
                .map(entry -> new ExchangeRateDto(
                        SOURCE,
                        baseCurrency,
                        entry.getKey(),
                        new BigDecimal(entry.getValue().toString()),
                        null,
                        null,
                        fetchedAt,
                        publishedAt,
                        Map.of("currency", entry.getKey(), "rate", entry.getValue(), "base", baseCurrency, "date", dateStr)))
                .toList();
    }

    private Instant parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse Frankfurter date '{}': {}", dateStr, e.getMessage());
            return Instant.now();
        }
    }
}