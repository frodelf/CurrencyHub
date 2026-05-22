package com.example.currencyhub.common.api;

import com.example.currencyhub.common.exception.ValidationError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiError {
    private int status;
    private String error;
    private String message;
    private String path;
    private Instant timestamp;
    private List<ValidationError> errors;

    public ApiError(int status, String error, String message, String path, Instant timestamp) {
        this(status, error, message, path, timestamp, null);
    }
}
