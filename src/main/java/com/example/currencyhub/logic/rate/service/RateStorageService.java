package com.example.currencyhub.logic.rate.service;

import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.logic.rate.entity.ExchangeRate;
import com.example.currencyhub.logic.rate.entity.RateMetadata;
import com.example.currencyhub.logic.rate.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateStorageService {

    private final ExchangeRateRepository repository;
    private final MongoTemplate mongoTemplate;

    public void saveRates(List<ExchangeRateDto> rates, String jobId, boolean isBackfill) {
        if (rates.isEmpty()) return;

        List<ExchangeRate> entities = rates.stream()
                .map(dto -> toEntity(dto, jobId, isBackfill))
                .toList();

        repository.saveAll(entities);
        log.debug("[{}] Saved {} records to MongoDB (backfill={})", jobId, entities.size(), isBackfill);
    }

    public Page<ExchangeRate> getHistory(String source, String baseCurrency, String currency,
                                          Instant from, Instant to, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where("baseCurrency").is(baseCurrency));
        query.addCriteria(Criteria.where("currency").is(currency));
        query.addCriteria(Criteria.where("fetchedAt").gte(from).lte(to));
        if (source != null && !source.isBlank()) {
            query.addCriteria(Criteria.where("source").is(source.toUpperCase()));
        }

        long count = mongoTemplate.count(query, ExchangeRate.class);
        query.with(pageable);
        List<ExchangeRate> content = mongoTemplate.find(query, ExchangeRate.class);

        return new PageImpl<>(content, pageable, count);
    }

    private ExchangeRate toEntity(ExchangeRateDto dto, String jobId, boolean isBackfill) {
        return ExchangeRate.builder()
                .success(true)
                .source(dto.getSource())
                .baseCurrency(dto.getBaseCurrency())
                .currency(dto.getCurrency())
                .rate(dto.getRate())
                .buyRate(dto.getBuyRate())
                .sellRate(dto.getSellRate())
                .fetchedAt(dto.getFetchedAt())
                .providerPublishedAt(dto.getProviderPublishedAt())
                .rawPayload(dto.getRawPayload())
                .metadata(RateMetadata.builder()
                        .jobId(jobId)
                        .backfill(isBackfill)
                        .build())
                .build();
    }
}
