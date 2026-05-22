package com.example.currencyhub.logic.job.repository;

import com.example.currencyhub.logic.job.entity.JobDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDocumentRepository extends MongoRepository<JobDocument, String> {
}
