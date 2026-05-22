package com.example.currencyhub.common.api.ecb;

import com.example.currencyhub.common.api.ExchangeRateProvider;
import com.example.currencyhub.common.exception.ProviderClientException;
import com.example.currencyhub.common.exception.ProviderHttpException;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EcbClient implements ExchangeRateProvider {

    private static final String SOURCE = "ECB";

    private final AppProperties properties;
    private final HttpClient httpClient;

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public boolean supportsCurrentRates() {
        return true;
    }

    @Override
    public boolean supportsHistoricalRates() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return properties.getProviders().getEcb().isEnabled();
    }

    @Override
    @Retryable(
            retryFor = {ProviderHttpException.class, IOException.class},
            maxAttemptsExpression = "${app.external-api.retry-attempts}",
            backoff = @Backoff(
                    delayExpression = "${app.external-api.retry-initial-delay-ms}",
                    multiplier = 2.0,
                    maxDelayExpression = "${app.external-api.retry-max-delay-ms}"
            )
    )
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getEcb().getBaseUrl() + "/eurofxref-daily.xml";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getExternalApi().getTimeoutSeconds()))
                .header("Accept", "application/xml, text/xml")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 400 && status < 500 && status != 429) {
            throw new ProviderClientException(SOURCE, status, "HTTP " + status + " from ECB");
        }
        if (status != 200) {
            throw new ProviderHttpException(SOURCE, status, "HTTP " + status + " from ECB");
        }

        return parseXml(response.body());
    }

    private List<ExchangeRateDto> parseXml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        doc.getDocumentElement().normalize();

        NodeList cubeNodes = doc.getElementsByTagName("Cube");
        Instant fetchedAt = Instant.now();
        Instant publishedAt = fetchedAt;
        List<ExchangeRateDto> result = new ArrayList<>();

        for (int i = 0; i < cubeNodes.getLength(); i++) {
            Element cube = (Element) cubeNodes.item(i);

            if (cube.hasAttribute("time")) {
                try {
                    publishedAt = LocalDate.parse(cube.getAttribute("time"))
                            .atStartOfDay(ZoneOffset.UTC).toInstant();
                } catch (Exception e) {
                    log.warn("Could not parse ECB date: {}", cube.getAttribute("time"));
                }
                continue;
            }

            if (cube.hasAttribute("currency") && cube.hasAttribute("rate")) {
                String currency = cube.getAttribute("currency");
                BigDecimal rate = new BigDecimal(cube.getAttribute("rate"));
                Map<String, String> raw = Map.of("currency", currency, "rate", cube.getAttribute("rate"));
                result.add(new ExchangeRateDto(SOURCE, "EUR", currency, rate, null, null,
                        fetchedAt, publishedAt, raw));
            }
        }

        return result;
    }
}
