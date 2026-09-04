package com.example.currencyhub.logic.provider.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProviderInfoResponse {
    private String source;
    private boolean currentSupported;
    private boolean historicalSupported;
    private boolean enabled;
}
