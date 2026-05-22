package com.example.currencyhub.logic.rate.service;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.config.AppProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateCacheService {

    private static final String KEY_PREFIX = "rates:current:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public void saveRates(List<ExchangeRateDto> rates) {
        Duration ttl = Duration.ofMinutes(properties.getRedis().getCurrentRatesTtlMinutes());

        for (ExchangeRateDto rate : rates) {
            String key = buildKey(rate.getSource(), rate.getBaseCurrency(), rate.getCurrency());
            try {
                String value = objectMapper.writeValueAsString(rate);
                redisTemplate.opsForValue().set(key, value, ttl);
            } catch (JacksonException e) {
                log.error("Failed to save rate to Redis [key={}]: {}", key, e.getMessage());
            }
        }
    }

    public List<ExchangeRateDto> getAllRates(String source, String baseCurrency, String currency) {
        String pattern = KEY_PREFIX
                + (source != null ? source.toUpperCase() : "*") + ":"
                + (baseCurrency != null ? baseCurrency.toUpperCase() : "*") + ":"
                + (currency != null ? currency.toUpperCase() : "*");

        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return List.of();

        return keys.stream()
                .map(k -> redisTemplate.opsForValue().get(k))
                .filter(Objects::nonNull)
                .map(v -> {
                    try {
                        return objectMapper.readValue(v, ExchangeRateDto.class);
                    } catch (JacksonException e) {
                        log.error("Failed to deserialize rate from Redis: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    public Optional<ExchangeRateDto> getRate(String source, String baseCurrency, String currency) {
        String key = buildKey(source.toUpperCase(), baseCurrency.toUpperCase(), currency.toUpperCase());
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(value, ExchangeRateDto.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize rate from Redis [key={}]: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private String buildKey(String source, String baseCurrency, String currency) {
        return KEY_PREFIX + source + ":" + baseCurrency + ":" + currency;
    }
}
