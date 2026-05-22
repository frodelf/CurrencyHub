package com.example.currencyhub.common.api;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;

import java.util.List;

public interface ExchangeRateProvider {

    String getSource();

    boolean supportsCurrentRates();

    boolean supportsHistoricalRates();

    boolean isEnabled();

    List<ExchangeRateDto> fetchCurrentRates() throws Exception;
}
