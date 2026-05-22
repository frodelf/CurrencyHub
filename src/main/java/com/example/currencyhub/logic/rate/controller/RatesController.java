package com.example.currencyhub.logic.rate.controller;

import com.example.currencyhub.logic.job.model.response.JobStartedResponse;
import com.example.currencyhub.logic.rate.model.request.ManualRefreshRequest;
import com.example.currencyhub.logic.rate.model.response.CurrentRateResponse;
import com.example.currencyhub.logic.rate.model.response.HistoryRateResponse;
import com.example.currencyhub.logic.rate.service.RateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/rates")
@RequiredArgsConstructor
public class RatesController {

    private final RateService rateService;

    @GetMapping("/current")
    public ResponseEntity<List<CurrentRateResponse>> getCurrentRates(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String baseCurrency,
            @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(rateService.getCurrentRates(source, baseCurrency, currency));
    }

    @GetMapping("/current/{source}/{baseCurrency}/{currency}")
    public ResponseEntity<CurrentRateResponse> getCurrentRate(
            @PathVariable String source,
            @PathVariable String baseCurrency,
            @PathVariable String currency) {
        return rateService.getCurrentRate(source, baseCurrency, currency)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/history")
    public ResponseEntity<Page<HistoryRateResponse>> getHistory(
            @RequestParam(required = false) String source,
            @RequestParam String baseCurrency,
            @RequestParam String currency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(rateService.getHistory(source, baseCurrency, currency, from, to, page, size));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JobStartedResponse> manualRefresh(
            @RequestBody(required = false) ManualRefreshRequest request) {
        List<String> sources = request != null ? request.getSources() : null;
        return ResponseEntity.accepted().body(rateService.triggerRefresh(sources));
    }
}
