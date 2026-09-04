package com.example.currencyhub.common.api.ecb;

import com.example.currencyhub.common.api.ExchangeRateProvider;
import com.example.currencyhub.common.api.ProviderHttpExecutor;
import com.example.currencyhub.logic.rate.model.ExchangeRateDto;
import com.example.currencyhub.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EcbClient implements ExchangeRateProvider {

    private static final String SOURCE = "ECB";

    private final AppProperties properties;
    private final ProviderHttpExecutor httpExecutor;

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
    public List<ExchangeRateDto> fetchCurrentRates() throws Exception {
        String url = properties.getProviders().getEcb().getBaseUrl() + "/eurofxref-daily.xml";
        var response = httpExecutor.get(SOURCE, url, "application/xml, text/xml", Map.of());
        return parseXml(response.body());
    }

    private List<ExchangeRateDto> parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        XPath xpath = XPathFactory.newInstance().newXPath();

        Instant fetchedAt = Instant.now();
        Instant publishedAt = fetchedAt;

        Element timeCube = (Element) xpath.evaluate(
                "//*[local-name()='Cube'][@time]", doc, XPathConstants.NODE);
        if (timeCube != null) {
            try {
                publishedAt = LocalDate.parse(timeCube.getAttribute("time"))
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                log.warn("Could not parse ECB date '{}': {}", timeCube.getAttribute("time"), e.getMessage());
            }
        }

        NodeList rateNodes = (NodeList) xpath.evaluate(
                "//*[local-name()='Cube'][@currency]", doc, XPathConstants.NODESET);

        List<ExchangeRateDto> result = new ArrayList<>();
        for (int i = 0; i < rateNodes.getLength(); i++) {
            Element cube = (Element) rateNodes.item(i);
            String currency = cube.getAttribute("currency");
            BigDecimal rate = new BigDecimal(cube.getAttribute("rate"));
            Map<String, String> raw = Map.of("currency", currency, "rate", cube.getAttribute("rate"));
            result.add(new ExchangeRateDto(SOURCE, "EUR", currency, rate, null, null,
                    fetchedAt, publishedAt, raw));
        }

        return result;
    }
}