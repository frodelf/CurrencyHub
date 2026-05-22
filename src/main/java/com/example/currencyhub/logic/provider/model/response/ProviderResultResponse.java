package com.example.currencyhub.logic.provider.model.response;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderResultResponse {
    private String source;
    private List<ExchangeRateDto> rates;
    private boolean success;
    private String errorMessage;

    public static ProviderResultResponse success(String source, List<ExchangeRateDto> rates) {
        return new ProviderResultResponse(source, rates, true, null);
    }

    public static ProviderResultResponse failure(String source, String errorMessage) {
        return new ProviderResultResponse(source, List.of(), false, errorMessage);
    }
}
