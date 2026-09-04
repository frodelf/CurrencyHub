package com.example.currencyhub.common.api.nbu;

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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class NbuClient implements BackfillCapableProvider {

    private static final String SOURCE = "NBU";
    private static final DateTimeFormatter NBU_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

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
        return properties.getProviders().getNbu().isEnabled();
    }

    @Override
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getNbu().getBaseUrl() + "?json";
        return fetchFromUrl(url, Instant.now(), null);
    }

    @Override
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        String dateStr = date.format(URL_DATE_FORMAT);
        String url = properties.getProviders().getNbu().getBaseUrl() + "?date=" + dateStr + "&json";
        Instant publishedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return fetchFromUrl(url, Instant.now(), publishedAt);
    }

    private List<ExchangeRateDto> fetchFromUrl(String url, Instant fetchedAt, Instant overridePublishedAt)
            throws Exception {
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of());

        List<Map<String, Object>> rawList = objectMapper.readValue(response.body(), new TypeReference<>() {});

        return rawList.stream()
                .map(item -> toDto(item, fetchedAt, overridePublishedAt))
                .filter(Objects::nonNull)
                .toList();
    }

    private ExchangeRateDto toDto(Map<String, Object> item, Instant fetchedAt, Instant overridePublishedAt) {
        Object ccObj = item.get("cc");
        Object rateObj = item.get("rate");
        if (ccObj == null || rateObj == null) {
            log.warn("NBU item missing 'cc' or 'rate' field: {}", item);
            return null;
        }

        BigDecimal rate = new BigDecimal(rateObj.toString());
        Instant publishedAt = overridePublishedAt != null
                ? overridePublishedAt
                : parseExchangeDate((String) item.get("exchangedate"));

        return new ExchangeRateDto(SOURCE, "UAH", (String) ccObj, rate, null, null, fetchedAt, publishedAt, item);
    }

    private Instant parseExchangeDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, NBU_DATE_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse NBU date '{}': {}", dateStr, e.getMessage());
            return Instant.now();
        }
    }
}