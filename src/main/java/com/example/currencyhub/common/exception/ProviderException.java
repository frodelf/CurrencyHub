package com.example.currencyhub.common.exception;

public class ProviderException extends RuntimeException {

    private final String source;
    private final int statusCode;
    private final boolean retryable;

    public ProviderException(String source, int statusCode, String message, boolean retryable) {
        super(message);
        this.source = source;
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public String getSource() {
        return source;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }
}