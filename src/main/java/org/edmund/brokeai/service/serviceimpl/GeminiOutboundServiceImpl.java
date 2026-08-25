package org.edmund.brokeai.service.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.exception.AiProcessingException;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class GeminiOutboundServiceImpl implements GeminiOutboundService {

    private final RestTemplate restTemplate;
    private final String baseUrlTemplate;
    private final List<String> models;

    public GeminiOutboundServiceImpl(
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            @Value("${gemini.api.base-url}") String baseUrlTemplate,
            @Value("${gemini.api.models}") List<String> models
    ) {
        this.restTemplate = restTemplate;
        this.baseUrlTemplate = baseUrlTemplate;
        this.models = models.stream()
                .map(String::trim)
                .filter(model -> !model.isEmpty())
                .toList();

        if (this.models.isEmpty()) {
            throw new IllegalArgumentException("At least one Gemini model must be configured");
        }
    }

    @Override
    public GeminiResponse sendToGemini(GeminiRequest request) {
        RuntimeException lastRetryableFailure = null;

        for (String model : models) {
            String targetUrl = String.format(baseUrlTemplate, model);

            try {
                log.debug("Sending request to Gemini model {}", model);
                GeminiResponse response = restTemplate.postForObject(targetUrl, request, GeminiResponse.class);
                log.info("Gemini request completed using model {}", model);
                return response;
            } catch (RestClientResponseException exception) {
                int statusCode = exception.getStatusCode().value();
                if (statusCode == 429 || statusCode == 503) {
                    lastRetryableFailure = exception;
                    log.warn("Gemini model {} returned HTTP {}; trying the next configured model", model, statusCode);
                    continue;
                }

                log.error("Gemini model {} returned terminal HTTP {}", model, statusCode);
                throw new AiProcessingException(
                        "Gemini request failed on model " + model + " with HTTP " + statusCode,
                        exception
                );
            } catch (ResourceAccessException exception) {
                lastRetryableFailure = exception;
                log.warn("Could not reach Gemini model {}; trying the next configured model", model);
            } catch (RestClientException exception) {
                log.error(
                        "Gemini request failed on model {} ({} caused by {})",
                        model,
                        exception.getClass().getSimpleName(),
                        rootCauseType(exception)
                );
                throw new AiProcessingException("Gemini request failed on model " + model, exception);
            }
        }

        throw new AiProcessingException("All configured Gemini models failed", lastRetryableFailure);
    }

    private String rootCauseType(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getSimpleName();
    }
}
