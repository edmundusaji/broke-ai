package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO to send JSON => Gemini API.
 */
public record GeminiRequest(List<Content> contents) {

    public record Content(List<Part> parts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, InlineData inlineData) {}

    public record InlineData(String mimeType, String data) {}
}