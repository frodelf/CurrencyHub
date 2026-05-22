package com.example.currencyhub.common.api.nbu;

import com.example.currencyhub.common.api.BackfillCapableProvider;
import com.example.currencyhub.common.exception.ProviderException;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.config.AppProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class NbuClient implements BackfillCapableProvider {

    private static final String SOURCE = "NBU";
    private static final DateTimeFormatter NBU_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppProperties properties;
    private final HttpClient httpClient;
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
    @Retryable(
            retryFor = {ProviderException.class, IOException.class},
            exceptionExpression = "#{#root instanceof T(java.io.IOException) or #root.retryable}",
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getNbu().getBaseUrl() + "?json";
        return fetchFromUrl(url, Instant.now(), null);
    }

    @Override
    @Retryable(
            retryFor = {ProviderException.class, IOException.class},
            exceptionExpression = "#{#root instanceof T(java.io.IOException) or #root.retryable}",
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        String dateStr = date.format(URL_DATE_FORMAT);
        String url = properties.getProviders().getNbu().getBaseUrl() + "?date=" + dateStr + "&json";
        Instant publishedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        return fetchFromUrl(url, Instant.now(), publishedAt);
    }

    private List<ExchangeRateDto> fetchFromUrl(String url, Instant fetchedAt, Instant overridePublishedAt)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 400 && status < 500 && status != 429) {
            throw new ProviderException(SOURCE, status, "HTTP " + status + " from NBU", false);
        }
        if (status != 200) {
            throw new ProviderException(SOURCE, status, "HTTP " + status + " from NBU", true);
        }

        List<Map<String, Object>> rawList = objectMapper.readValue(response.body(), new TypeReference<>() {});

        return rawList.stream()
                .map(item -> toDto(item, fetchedAt, overridePublishedAt))
                .toList();
    }

    private ExchangeRateDto toDto(Map<String, Object> item, Instant fetchedAt, Instant overridePublishedAt) {
        String cc = (String) item.get("cc");
        BigDecimal rate = new BigDecimal(item.get("rate").toString());
        Instant publishedAt = overridePublishedAt != null
                ? overridePublishedAt
                : parseExchangeDate((String) item.get("exchangedate"));

        return new ExchangeRateDto(SOURCE, "UAH", cc, rate, null, null, fetchedAt, publishedAt, item);
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
