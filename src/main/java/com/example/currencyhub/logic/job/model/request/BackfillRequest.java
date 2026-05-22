package com.example.currencyhub.logic.job.model.request;

import jakarta.validation.constraints.Max;
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

    @NotEmpty(message = "At least one source is required")
    private List<String> sources;

    @NotNull(message = "from date is required")
    private LocalDate from;

    @NotNull(message = "to date is required")
    private LocalDate to;

    private List<String> baseCurrencies;
    private List<String> currencies;

    @Min(value = 1, message = "concurrencyLimit must be at least 1")
    @Max(value = 30, message = "concurrencyLimit must not exceed 30")
    private Integer concurrencyLimit;
}
