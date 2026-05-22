package com.example.currencyhub.logic.rate.mapper;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.logic.rate.entity.ExchangeRate;
import com.example.currencyhub.logic.rate.model.response.CurrentRateResponse;
import com.example.currencyhub.logic.rate.model.response.HistoryRateResponse;
import org.springframework.stereotype.Component;

@Component
public class RateMapper {

    public CurrentRateResponse toCurrentRateResponse(ExchangeRateDto dto) {
        return new CurrentRateResponse(
                dto.getSource(),
                dto.getBaseCurrency(),
                dto.getCurrency(),
                dto.getRate(),
                dto.getBuyRate(),
                dto.getSellRate(),
                dto.getFetchedAt());
    }

    public HistoryRateResponse toHistoryRateResponse(ExchangeRate entity) {
        boolean isBackfill = entity.getMetadata() != null && entity.getMetadata().isBackfill();
        return new HistoryRateResponse(
                entity.getId(),
                entity.isSuccess(),
                entity.getErrorMessage(),
                entity.getSource(),
                entity.getBaseCurrency(),
                entity.getCurrency(),
                entity.getRate(),
                entity.getBuyRate(),
                entity.getSellRate(),
                entity.getFetchedAt(),
                entity.getProviderPublishedAt(),
                isBackfill);
    }
}
