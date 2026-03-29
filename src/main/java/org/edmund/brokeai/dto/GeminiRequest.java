package org.edmund.brokeai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Struktur DTO untuk mengirim format JSON ke Gemini API.
 * Menggunakan 'record' (fitur modern Java) agar kode ringkas tanpa Lombok.
 */
public record GeminiRequest(List<Content> contents) {

    public record Content(List<Part> parts) {}

    // @JsonInclude(JsonInclude.Include.NON_NULL) sangat penting di sini.
    // Tujuannya agar jika kita hanya mengirim teks (image-nya null),
    // Jackson tidak akan mencetak "inlineData": null ke dalam JSON,
    // yang bisa membuat Google API bingung/error.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, InlineData inlineData) {}

    public record InlineData(String mimeType, String data) {}
}