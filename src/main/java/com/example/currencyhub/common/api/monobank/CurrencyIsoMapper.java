package com.example.currencyhub.common.api.monobank;

import org.springframework.stereotype.Component;

import java.util.Currency;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CurrencyIsoMapper {

    private static final Map<Integer, String> CACHE = new ConcurrentHashMap<>();

    static {
        Currency.getAvailableCurrencies()
                .forEach(c -> CACHE.put(c.getNumericCode(), c.getCurrencyCode()));
    }

    public Optional<String> fromNumericCode(int numericCode) {
        return Optional.ofNullable(CACHE.get(numericCode));
    }
}
