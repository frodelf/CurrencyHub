package com.example.currencyhub.common.api.monobank;

import com.example.currencyhub.common.api.ExchangeRateProvider;
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
    private final ProviderHttpExecutor httpExecutor;
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
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getMonobank().getBaseUrl() + "/bank/currency";
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of());

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
            log.debug("Could not parse Monobank numeric value '{}': {}", value, e.getMessage());
            return null;
        }
    }
}