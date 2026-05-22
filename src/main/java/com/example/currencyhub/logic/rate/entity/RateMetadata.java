package com.example.currencyhub.logic.rate.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateMetadata {

    private String requestUrl;
    private String jobId;

    @Field("isBackfill")
    private boolean backfill;
}
