package com.example.currencyhub.logic.job.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDocument {

    @Id
    private String id;

    private JobType type;
    private JobStatus status;

    private Instant startedAt;
    private Instant finishedAt;

    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int savedRecords;

    private String errorMessage;
}
