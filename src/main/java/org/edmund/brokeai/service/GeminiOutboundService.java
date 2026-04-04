package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.GeminiRequest;
import org.edmund.brokeai.dto.GeminiResponse;

public interface GeminiOutboundService {
    GeminiResponse sendToGemini(GeminiRequest request);
    GeminiResponse sendTextToGemini(String text);
}