package org.edmund.brokeai.serviceImpl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.exception.AiProcessingException;
import org.edmund.brokeai.service.serviceimpl.GeminiOutboundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiOutboundServiceImplTest {

    private static final String BASE_URL = "https://gemini.test/models/%s:generateContent";
    private static final String PRIMARY_URL = "https://gemini.test/models/primary:generateContent";
    private static final String FALLBACK_URL = "https://gemini.test/models/fallback:generateContent";

    @Mock
    private RestTemplate restTemplate;

    private GeminiOutboundServiceImpl service;
    private GeminiRequest request;
    private GeminiResponse response;

    @BeforeEach
    void setUp() {
        service = new GeminiOutboundServiceImpl(restTemplate, BASE_URL, List.of("primary", "fallback"));
        request = new GeminiRequest(List.of());
        response = new GeminiResponse(List.of());
    }

    @Test
    void sendToGemini_PrimarySucceeds_DoesNotCallFallback() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class)).thenReturn(response);

        assertEquals(response, service.sendToGemini(request));

        verify(restTemplate, never()).postForObject(FALLBACK_URL, request, GeminiResponse.class);
    }

    @Test
    void sendToGemini_RateLimited_UsesFallback() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class))
                .thenThrow(http429());
        when(restTemplate.postForObject(FALLBACK_URL, request, GeminiResponse.class)).thenReturn(response);

        assertEquals(response, service.sendToGemini(request));

        verify(restTemplate).postForObject(FALLBACK_URL, request, GeminiResponse.class);
    }

    @Test
    void sendToGemini_ServiceUnavailable_UsesFallback() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class))
                .thenThrow(http503());
        when(restTemplate.postForObject(FALLBACK_URL, request, GeminiResponse.class)).thenReturn(response);

        assertEquals(response, service.sendToGemini(request));
    }

    @Test
    void sendToGemini_ConnectionFailure_UsesFallback() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class))
                .thenThrow(new ResourceAccessException("connection timed out"));
        when(restTemplate.postForObject(FALLBACK_URL, request, GeminiResponse.class)).thenReturn(response);

        assertEquals(response, service.sendToGemini(request));
    }

    @Test
    void sendToGemini_BadRequest_StopsFallbackChain() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class))
                .thenThrow(http400());

        AiProcessingException exception = assertThrows(
                AiProcessingException.class,
                () -> service.sendToGemini(request)
        );

        assertTrue(exception.getMessage().contains("HTTP 400"));
        verify(restTemplate, never()).postForObject(FALLBACK_URL, request, GeminiResponse.class);
    }

    @Test
    void sendToGemini_AllModelsRetryableFailures_ThrowsAiProcessingException() {
        when(restTemplate.postForObject(PRIMARY_URL, request, GeminiResponse.class)).thenThrow(http429());
        when(restTemplate.postForObject(FALLBACK_URL, request, GeminiResponse.class)).thenThrow(http503());

        AiProcessingException exception = assertThrows(
                AiProcessingException.class,
                () -> service.sendToGemini(request)
        );

        assertEquals("All configured Gemini models failed", exception.getMessage());
        assertTrue(exception.getCause() instanceof HttpServerErrorException.ServiceUnavailable);
    }

    @Test
    void geminiResponse_ProviderMetadata_DoesNotBreakStrictDeserialization() throws Exception {
        ObjectMapper strictMapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        GeminiResponse parsed = strictMapper.readValue("""
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "{\\"amount\\": 25000}", "thoughtSignature": "signature"}],
                      "role": "model"
                    },
                    "finishReason": "STOP",
                    "index": 0
                  }],
                  "usageMetadata": {"promptTokenCount": 42},
                  "modelVersion": "gemini-3.6-flash",
                  "responseId": "response-id"
                }
                """, GeminiResponse.class);

        assertEquals("{\"amount\": 25000}", parsed.candidates().getFirst().content().parts().getFirst().text());
    }

    private HttpClientErrorException http400() {
        return HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8
        );
    }

    private HttpClientErrorException http429() {
        return HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "Rate limited", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8
        );
    }

    private HttpServerErrorException http503() {
        return HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8
        );
    }
}
