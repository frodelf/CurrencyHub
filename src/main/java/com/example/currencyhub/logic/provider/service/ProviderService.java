package com.example.currencyhub.logic.provider.service;

import com.example.currencyhub.common.api.ExchangeRateProvider;
import com.example.currencyhub.logic.provider.model.response.ProviderInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderService {

    private final List<ExchangeRateProvider> providers;

    public List<ProviderInfoResponse> getProviders() {
        return providers.stream()
                .map(p -> new ProviderInfoResponse(p.getSource(), p.supportsCurrentRates(), p.supportsHistoricalRates(), p.isEnabled()))
                .toList();
    }
}
