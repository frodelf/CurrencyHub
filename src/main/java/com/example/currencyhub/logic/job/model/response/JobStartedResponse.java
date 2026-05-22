package com.example.currencyhub.logic.job.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobStartedResponse {
    private String jobId;
    private String status;
}
