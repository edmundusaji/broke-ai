package org.edmund.brokeai.service.serviceimpl;

import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;
import org.edmund.brokeai.service.GeminiOutboundService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class GeminiOutboundServiceImpl implements GeminiOutboundService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Override
    public GeminiResponse sendToGemini(GeminiRequest request) {

        String fullUrl = geminiApiUrl + geminiApiKey;
        RestTemplate restTemplate = new RestTemplate();

        System.out.println("🚀 [OUTBOUND] Request sent to Google Gemini API...");

        return restTemplate.postForObject(fullUrl, request, GeminiResponse.class);
    }
}