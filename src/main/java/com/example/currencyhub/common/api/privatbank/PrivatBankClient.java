package com.example.currencyhub.common.api.privatbank;

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
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PrivatBankClient implements BackfillCapableProvider {

    private static final String SOURCE = "PRIVATBANK";
    private static final DateTimeFormatter PB_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
        return properties.getProviders().getPrivatbank().isEnabled();
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
        String url = properties.getProviders().getPrivatbank().getBaseUrl() + "/pubinfo?exchange&coursid=5";
        HttpResponse<String> response = doGet(url);
        ensureSuccess(response.statusCode(), url);

        List<Map<String, Object>> rawList = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Instant fetchedAt = Instant.now();

        return rawList.stream()
                .map(item -> new ExchangeRateDto(
                        SOURCE,
                        (String) item.get("base_ccy"),
                        (String) item.get("ccy"),
                        null,
                        parseBigDecimal(item.get("buy")),
                        parseBigDecimal(item.get("sale")),
                        fetchedAt,
                        fetchedAt,
                        item))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
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
        String dateStr = date.format(PB_DATE_FORMAT);
        String url = properties.getProviders().getPrivatbank().getBaseUrl()
                + "/exchange_rates?json&date=" + dateStr;
        HttpResponse<String> response = doGet(url);
        ensureSuccess(response.statusCode(), url);

        Map<String, Object> rawMap = objectMapper.readValue(response.body(), new TypeReference<>() {});
        List<Map<String, Object>> rates = (List<Map<String, Object>>) rawMap.get("exchangeRate");

        Instant fetchedAt = Instant.now();
        Instant publishedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant();

        return rates.stream()
                .filter(item -> item.get("currency") != null && !"UAH".equals(item.get("currency")))
                .map(item -> new ExchangeRateDto(
                        SOURCE,
                        (String) item.get("baseCurrency"),
                        (String) item.get("currency"),
                        parseBigDecimal(item.get("saleRateNB")),
                        parseBigDecimal(item.get("purchaseRate")),
                        parseBigDecimal(item.get("saleRate")),
                        fetchedAt,
                        publishedAt,
                        item))
                .toList();
    }

    private HttpResponse<String> doGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void ensureSuccess(int statusCode, String url) {
        if (statusCode >= 400 && statusCode < 500 && statusCode != 429) {
            throw new ProviderException(SOURCE, statusCode, "HTTP " + statusCode + " from " + url, false);
        }
        if (statusCode != 200) {
            throw new ProviderException(SOURCE, statusCode, "HTTP " + statusCode + " from " + url, true);
        }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.debug("Could not parse PrivatBank numeric value '{}': {}", value, e.getMessage());
            return null;
        }
    }
}
