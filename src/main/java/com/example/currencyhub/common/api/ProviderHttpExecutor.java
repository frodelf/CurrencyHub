package com.example.currencyhub.common.api;

import com.example.currencyhub.common.exception.ProviderException;
import com.example.currencyhub.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProviderHttpExecutor {

    private final HttpClient httpClient;
    private final AppProperties properties;

    @Retryable(
            retryFor = {ProviderException.class, IOException.class},
            exceptionExpression = "#{#root instanceof T(java.io.IOException) or #root.retryable}",
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public HttpResponse<String> get(String source, String url, String acceptHeader, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", acceptHeader)
                .GET();
        headers.forEach(builder::header);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        ensureSuccess(source, response.statusCode());
        return response;
    }

    private void ensureSuccess(String source, int status) {
        if (status >= 400 && status < 500 && status != 429) {
            throw new ProviderException(source, status, "HTTP " + status + " from " + source, false);
        }
        if (status != 200) {
            throw new ProviderException(source, status, "HTTP " + status + " from " + source, true);
        }
    }
}