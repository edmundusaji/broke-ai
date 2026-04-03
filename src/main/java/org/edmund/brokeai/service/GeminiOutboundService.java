package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.GeminiResponse;
import org.springframework.web.multipart.MultipartFile;

public interface GeminiOutboundService {
    GeminiResponse sendToGemini(MultipartFile file, String base64EncodedImage);
}