package com.example.currencyhub.logic.job.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "jobs")
@CompoundIndexes({
        @CompoundIndex(name = "idx_type_status", def = "{'type': 1, 'status': 1}")
})
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

    @Indexed(name = "idx_finished_at_ttl", expireAfter = "30d")
    private Instant finishedAt;

    private int totalTasks;
    private int completedTasks;
    private int failedTasks;
    private int savedRecords;

    private String errorMessage;
}
