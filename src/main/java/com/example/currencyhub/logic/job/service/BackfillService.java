package com.example.currencyhub.logic.job.service;

import com.example.currencyhub.common.api.BackfillCapableProvider;
import com.example.currencyhub.common.exception.ValidationError;
import com.example.currencyhub.common.exception.ValidationException;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.logic.job.model.response.JobStartedResponse;
import com.example.currencyhub.logic.job.entity.JobStatus;
import com.example.currencyhub.logic.job.entity.JobType;
import com.example.currencyhub.config.AppProperties;
import com.example.currencyhub.logic.job.entity.JobDocument;
import com.example.currencyhub.logic.job.model.request.BackfillRequest;
import com.example.currencyhub.logic.job.model.response.BackfillStatusResponse;
import com.example.currencyhub.logic.job.repository.JobDocumentRepository;
import com.example.currencyhub.logic.rate.service.RateStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
@RequiredArgsConstructor
public class BackfillService {

    private final List<BackfillCapableProvider> backfillProviders;
    private final RateStorageService rateStorageService;
    private final JobDocumentRepository jobDocumentRepository;
    private final MongoTemplate mongoTemplate;
    private final AppProperties properties;

    private final ExecutorService virtualThreadExecutor;

    public JobStartedResponse startBackfill(BackfillRequest request) {
        if (request.getFrom().isAfter(request.getTo())) {
            throw new IllegalArgumentException("'from' date must not be after 'to' date");
        }
        int limit = resolveLimit(request.getConcurrencyLimit());

        String jobId = "bf-" + LocalDate.now() + "-" + UUID.randomUUID().toString().substring(0, 8);

        List<BackfillCapableProvider> backfillProviders = resolveProviders(request);
        List<LocalDate> dates = request.getFrom().datesUntil(request.getTo().plusDays(1)).toList();

        JobDocument job = JobDocument.builder()
                .id(jobId)
                .type(JobType.BACKFILL)
                .status(JobStatus.RUNNING)
                .startedAt(Instant.now())
                .totalTasks(backfillProviders.size() * dates.size())
                .build();
        jobDocumentRepository.save(job);

        virtualThreadExecutor.submit(() -> executeBackfill(request, jobId, backfillProviders, dates, limit));

        log.info("[{}] Backfill job submitted for sources={} from={} to={}",
                jobId, request.getSources(), request.getFrom(), request.getTo());
        return new JobStartedResponse(jobId, JobStatus.RUNNING.name());
    }

    public Optional<BackfillStatusResponse> getBackfillStatus(String jobId) {
        return jobDocumentRepository.findById(jobId).map(this::toStatusResponse);
    }

    private void executeBackfill(BackfillRequest request, String jobId,
            List<BackfillCapableProvider> backfillProviders, List<LocalDate> dates, int limit) {
        Semaphore semaphore = new Semaphore(limit);
        List<Runnable> tasks = buildTasks(backfillProviders, dates, jobId, semaphore, request);

        log.info("[{}] Starting backfill: {} providers × {} dates = {} tasks (concurrencyLimit={})",
                jobId, backfillProviders.size(), dates.size(), tasks.size(), limit);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            tasks.forEach(executor::submit);
        }

        finalizeJob(jobId);
    }

    private List<Runnable> buildTasks(List<BackfillCapableProvider> backfillProviders, List<LocalDate> dates,
            String jobId, Semaphore semaphore, BackfillRequest request) {
        List<Runnable> tasks = new ArrayList<>();
        for (BackfillCapableProvider provider : backfillProviders) {
            for (LocalDate date : dates) {
                tasks.add(() -> executeTask(provider, date, jobId, semaphore, request));
            }
        }
        return tasks;
    }

    private void executeTask(BackfillCapableProvider provider, LocalDate date, String jobId,
            Semaphore semaphore, BackfillRequest request) {
        try {
            semaphore.acquire();
            try {
                List<ExchangeRateDto> rates = provider.fetchHistoricalRates(date);
                List<ExchangeRateDto> filtered = applyFilters(rates, request);
                rateStorageService.saveRates(filtered, jobId, true);
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(jobId)),
                        new Update().inc("completedTasks", 1).inc("savedRecords", filtered.size()),
                        JobDocument.class);
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[{}] Task interrupted: provider={} date={}: {}", jobId, provider.getSource(), date, e.getMessage());
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(jobId)),
                    new Update().inc("failedTasks", 1),
                    JobDocument.class);
        } catch (Exception e) {
            log.error("[{}] Task failed: provider={} date={}: {}", jobId, provider.getSource(), date, e.getMessage());
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(jobId)),
                    new Update().inc("failedTasks", 1),
                    JobDocument.class);
        }
    }

    private void finalizeJob(String jobId) {
        JobDocument doc = jobDocumentRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));

        JobStatus finalStatus;
        if (doc.getFailedTasks() == 0) {
            finalStatus = JobStatus.COMPLETED;
        } else if (doc.getCompletedTasks() > 0) {
            finalStatus = JobStatus.PARTIAL_SUCCESS;
        } else {
            finalStatus = JobStatus.FAILED;
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(jobId)),
                new Update().set("status", finalStatus).set("finishedAt", Instant.now()),
                JobDocument.class);

        log.info("[{}] Backfill finished. status={} total={} completed={} failed={} savedRecords={}",
                jobId, finalStatus, doc.getTotalTasks(), doc.getCompletedTasks(),
                doc.getFailedTasks(), doc.getSavedRecords());
    }

    private BackfillStatusResponse toStatusResponse(JobDocument doc) {
        return new BackfillStatusResponse(
                doc.getId(),
                doc.getStatus().name(),
                doc.getStartedAt(),
                doc.getFinishedAt(),
                doc.getTotalTasks(),
                doc.getCompletedTasks(),
                doc.getFailedTasks(),
                doc.getSavedRecords());
    }

    private List<ExchangeRateDto> applyFilters(List<ExchangeRateDto> rates, BackfillRequest request) {
        List<String> currencies = toUpperCase(request.getCurrencies());
        List<String> baseCurrencies = toUpperCase(request.getBaseCurrencies());
        return rates.stream()
                .filter(r -> currencies.isEmpty() || currencies.contains(r.getCurrency()))
                .filter(r -> baseCurrencies.isEmpty() || baseCurrencies.contains(r.getBaseCurrency()))
                .toList();
    }

    private List<String> toUpperCase(List<String> values) {
        if (isEmpty(values)) return List.of();
        return values.stream().map(String::toUpperCase).toList();
    }

    private List<BackfillCapableProvider> resolveProviders(BackfillRequest request) {
        List<String> upperSources = request.getSources().stream().map(String::toUpperCase).toList();
        return backfillProviders.stream()
                .filter(BackfillCapableProvider::isEnabled)
                .filter(p -> upperSources.contains(p.getSource().toUpperCase()))
                .toList();
    }

    private int resolveLimit(Integer requested) {
        int max = properties.getBackfill().getMaxConcurrencyLimit();
        int def = properties.getBackfill().getDefaultConcurrencyLimit();
        if (requested == null) return def;
        if (requested > max) {
            throw new ValidationException(List.of(new ValidationError(
                    "concurrencyLimit", "concurrencyLimit must not exceed " + max)));
        }
        return requested;
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
