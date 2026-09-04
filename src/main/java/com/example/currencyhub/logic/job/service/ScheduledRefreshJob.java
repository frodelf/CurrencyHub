package com.example.currencyhub.logic.job.service;

import com.example.currencyhub.logic.job.entity.JobDocument;
import com.example.currencyhub.logic.job.entity.JobStatus;
import com.example.currencyhub.logic.job.entity.JobType;
import com.example.currencyhub.logic.job.repository.JobDocumentRepository;
import com.example.currencyhub.logic.provider.model.response.ProviderResultResponse;
import com.example.currencyhub.logic.provider.service.RatesFetchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledRefreshJob {

    private final RatesFetchService ratesFetchService;
    private final JobDocumentRepository jobDocumentRepository;

    @Scheduled(cron = "${app.scheduler.refresh-rate}")
    public void refresh() {
        String jobId = "refresh-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Instant startedAt = Instant.now();

        jobDocumentRepository.save(JobDocument.builder()
                .id(jobId)
                .type(JobType.REFRESH)
                .status(JobStatus.RUNNING)
                .startedAt(startedAt)
                .build());

        log.info("[{}] Scheduled refresh started at {}", jobId, startedAt);

        try {
            List<ProviderResultResponse> results = ratesFetchService.fetchAndStore(jobId, null);

            Instant finishedAt = Instant.now();
            long durationMs = Duration.between(startedAt, finishedAt).toMillis();
            long successCount = results.stream().filter(ProviderResultResponse::isSuccess).count();
            long errorCount = results.size() - successCount;
            long totalRates = results.stream().filter(ProviderResultResponse::isSuccess)
                    .mapToLong(r -> r.getRates().size()).sum();

            jobDocumentRepository.save(JobDocument.builder()
                    .id(jobId)
                    .type(JobType.REFRESH)
                    .status(resolveStatus(successCount, errorCount))
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .totalTasks(results.size())
                    .completedTasks((int) successCount)
                    .failedTasks((int) errorCount)
                    .savedRecords((int) totalRates)
                    .build());

            log.info("[{}] Scheduled refresh completed. duration={}ms providers={}/{} rates={} errors={}",
                    jobId, durationMs, successCount, results.size(), totalRates, errorCount);

            results.stream()
                    .filter(r -> !r.isSuccess())
                    .forEach(r -> log.warn("[{}] Provider {} failed: {}", jobId, r.getSource(), r.getErrorMessage()));
        } catch (Exception e) {
            log.error("[{}] Scheduled refresh failed unexpectedly: {}", jobId, e.getMessage(), e);
            jobDocumentRepository.save(JobDocument.builder()
                    .id(jobId)
                    .type(JobType.REFRESH)
                    .status(JobStatus.FAILED)
                    .startedAt(startedAt)
                    .finishedAt(Instant.now())
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    private JobStatus resolveStatus(long successCount, long errorCount) {
        if (errorCount == 0) return JobStatus.COMPLETED;
        if (successCount > 0) return JobStatus.PARTIAL_SUCCESS;
        return JobStatus.FAILED;
    }
}