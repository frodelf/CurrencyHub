package com.example.currencyhub.common.api.frankfurter;

import com.example.currencyhub.common.api.BackfillCapableProvider;
import com.example.currencyhub.common.exception.ProviderClientException;
import com.example.currencyhub.common.exception.ProviderHttpException;
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
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class FrankfurterClient implements BackfillCapableProvider {

    private static final String SOURCE = "FRANKFURTER";

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
        return properties.getProviders().getFrankfurter().isEnabled();
    }

    @Override
    @Retryable(
            retryFor = {ProviderHttpException.class, IOException.class},
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        return fetchFromUrl(properties.getProviders().getFrankfurter().getBaseUrl() + "/latest");
    }

    @Override
    @Retryable(
            retryFor = {ProviderHttpException.class, IOException.class},
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        return fetchFromUrl(properties.getProviders().getFrankfurter().getBaseUrl() + "/" + date);
    }

    @SuppressWarnings("unchecked")
    private List<ExchangeRateDto> fetchFromUrl(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 400 && status < 500 && status != 429) {
            throw new ProviderClientException(SOURCE, status, "HTTP " + status + " from Frankfurter");
        }
        if (status != 200) {
            throw new ProviderHttpException(SOURCE, status, "HTTP " + status + " from Frankfurter");
        }

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
                        rawMap))
                .toList();
    }

    private Instant parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
