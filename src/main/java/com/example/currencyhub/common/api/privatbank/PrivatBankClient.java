package com.example.currencyhub.common.api.privatbank;

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
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PrivatBankClient implements BackfillCapableProvider {

    private static final String SOURCE = "PRIVATBANK";
    private static final DateTimeFormatter PB_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
        return properties.getProviders().getPrivatbank().isEnabled();
    }

    @Override
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getPrivatbank().getBaseUrl() + "/pubinfo?exchange&coursid=5";
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of());

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
    public List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception {
        String dateStr = date.format(PB_DATE_FORMAT);
        String url = properties.getProviders().getPrivatbank().getBaseUrl()
                + "/exchange_rates?json&date=" + dateStr;
        var response = httpExecutor.get(SOURCE, url, "application/json", Map.of());

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