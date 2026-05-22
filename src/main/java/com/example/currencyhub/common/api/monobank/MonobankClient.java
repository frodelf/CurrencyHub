package com.example.currencyhub.common.api.monobank;

import com.example.currencyhub.common.api.ExchangeRateProvider;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class MonobankClient implements ExchangeRateProvider {

    private static final String SOURCE = "MONOBANK";
    private static final int UAH_CODE = 980;

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CurrencyIsoMapper currencyIsoMapper;

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
        return false;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProviders().getMonobank().isEnabled();
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
        String url = properties.getProviders().getMonobank().getBaseUrl() + "/bank/currency";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status == 429) {
            throw new ProviderHttpException(SOURCE, 429, "Monobank rate limit exceeded");
        }
        if (status >= 400 && status < 500) {
            throw new ProviderClientException(SOURCE, status, "HTTP " + status + " from Monobank");
        }
        if (status != 200) {
            throw new ProviderHttpException(SOURCE, status, "HTTP " + status + " from Monobank");
        }

        List<Map<String, Object>> rawList = objectMapper.readValue(response.body(), new TypeReference<>() {});
        Instant fetchedAt = Instant.now();
        List<ExchangeRateDto> result = new ArrayList<>();

        for (Map<String, Object> item : rawList) {
            int codeA = ((Number) item.get("currencyCodeA")).intValue();
            int codeB = ((Number) item.get("currencyCodeB")).intValue();

            if (codeB != UAH_CODE) continue;

            Optional<String> currencyA = currencyIsoMapper.fromNumericCode(codeA);
            Optional<String> currencyB = currencyIsoMapper.fromNumericCode(codeB);
            if (currencyA.isEmpty() || currencyB.isEmpty()) continue;

            long timestamp = ((Number) item.get("date")).longValue();
            Instant publishedAt = Instant.ofEpochSecond(timestamp);

            BigDecimal rateBuy = parseBigDecimalNonZero(item.get("rateBuy"));
            BigDecimal rateSell = parseBigDecimalNonZero(item.get("rateSell"));
            BigDecimal rateCross = parseBigDecimalNonZero(item.get("rateCross"));

            if (rateBuy != null) {
                result.add(new ExchangeRateDto(SOURCE, currencyB.get(), currencyA.get(),
                        null, rateBuy, rateSell, fetchedAt, publishedAt, item));
            } else if (rateCross != null) {
                result.add(new ExchangeRateDto(SOURCE, currencyB.get(), currencyA.get(),
                        rateCross, null, null, fetchedAt, publishedAt, item));
            }
        }

        return result;
    }

    private BigDecimal parseBigDecimalNonZero(Object value) {
        if (value == null) return null;
        try {
            BigDecimal bd = new BigDecimal(value.toString());
            return bd.compareTo(BigDecimal.ZERO) == 0 ? null : bd;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
