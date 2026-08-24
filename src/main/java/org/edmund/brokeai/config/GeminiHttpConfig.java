package org.edmund.brokeai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class GeminiHttpConfig {

    @Bean
    public RestTemplate geminiRestTemplate(
            RestTemplateBuilder builder,
            @Value("${gemini.api.connect-timeout:10s}") Duration connectTimeout,
            @Value("${gemini.api.read-timeout:30s}") Duration readTimeout
    ) {
        return builder
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
    }
}
