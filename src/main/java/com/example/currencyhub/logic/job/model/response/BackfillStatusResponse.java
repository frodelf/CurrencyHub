package com.example.currencyhub.logic.job.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BackfillStatusResponse {
    private String jobId;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int savedRecords;
}
