package com.example.currencyhub.logic.provider.controller;

import com.example.currencyhub.logic.provider.model.response.ProviderInfoResponse;
import com.example.currencyhub.logic.provider.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/providers")
@RequiredArgsConstructor
public class ProvidersController {

    private final ProviderService providerService;

    @GetMapping
    public ResponseEntity<List<ProviderInfoResponse>> getProviders() {
        return ResponseEntity.ok(providerService.getProviders());
    }
}
