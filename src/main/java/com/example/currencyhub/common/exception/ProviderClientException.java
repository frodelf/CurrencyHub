package com.example.currencyhub.common.exception;

public class ProviderClientException extends RuntimeException {

    private final String source;
    private final int statusCode;

    public ProviderClientException(String source, int statusCode, String message) {
        super(message);
        this.source = source;
        this.statusCode = statusCode;
    }

    public String getSource() {
        return source;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
