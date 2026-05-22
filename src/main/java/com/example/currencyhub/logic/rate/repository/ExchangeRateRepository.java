package com.example.currencyhub.logic.rate.repository;

import com.example.currencyhub.logic.rate.entity.ExchangeRate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRateRepository extends MongoRepository<ExchangeRate, String> {
}
