package com.example.currencyhub.logic.job.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BackfillRequest {

    @NotEmpty(message = "{backfill.sources.notEmpty}")
    private List<String> sources;

    @NotNull(message = "{backfill.from.notNull}")
    private LocalDate from;

    @NotNull(message = "{backfill.to.notNull}")
    private LocalDate to;

    private List<String> baseCurrencies;
    private List<String> currencies;

    @Min(value = 1, message = "{backfill.concurrencyLimit.min}")
    private Integer concurrencyLimit;
}
