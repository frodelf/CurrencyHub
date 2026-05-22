package com.example.currencyhub.logic.job.controller;

import com.example.currencyhub.util.ErrorUtils;
import com.example.currencyhub.logic.job.model.response.JobStartedResponse;
import com.example.currencyhub.logic.job.model.request.BackfillRequest;
import com.example.currencyhub.logic.job.model.response.BackfillStatusResponse;
import com.example.currencyhub.logic.job.service.BackfillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/backfill")
@RequiredArgsConstructor
public class BackfillController {

    private final BackfillService backfillService;

    @PostMapping
    public ResponseEntity<JobStartedResponse> startBackfill(
            @Valid @RequestBody BackfillRequest request, BindingResult bindingResult) {
        ErrorUtils.validate(bindingResult);
        return ResponseEntity.accepted().body(backfillService.startBackfill(request));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<BackfillStatusResponse> getStatus(@PathVariable String jobId) {
        return backfillService.getBackfillStatus(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
