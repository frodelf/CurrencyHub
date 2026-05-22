package com.example.currencyhub.common.api;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;

import java.time.LocalDate;
import java.util.List;

public interface BackfillCapableProvider extends ExchangeRateProvider {

    List<ExchangeRateDto> fetchHistoricalRates(LocalDate date) throws Exception;
}
