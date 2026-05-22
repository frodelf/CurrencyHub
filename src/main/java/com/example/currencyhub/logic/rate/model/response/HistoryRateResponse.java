package com.example.currencyhub.logic.rate.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HistoryRateResponse {
    private String id;
    private boolean success;
    private String errorMessage;
    private String source;
    private String baseCurrency;
    private String currency;
    private BigDecimal rate;
    private BigDecimal buyRate;
    private BigDecimal sellRate;
    private Instant fetchedAt;
    private Instant providerPublishedAt;
    private boolean backfill;
}
