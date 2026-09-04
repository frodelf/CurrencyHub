package com.example.currencyhub.logic.rate.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "exchange_rate")
@CompoundIndexes({
        @CompoundIndex(name = "idx_base_ccy_currency_fetched_at",
                def = "{'baseCurrency': 1, 'currency': 1, 'fetchedAt': 1}"),
        @CompoundIndex(name = "idx_source_base_ccy_currency_fetched_at",
                def = "{'source': 1, 'baseCurrency': 1, 'currency': 1, 'fetchedAt': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    private String id;

    @Field("isSuccess")
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
    private Object rawPayload;
    private RateMetadata metadata;
}
