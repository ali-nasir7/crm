package com.crm.modules.ai.service;

import com.crm.config.CrmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat-completions adapter. Activates only when CRM_AI_API_KEY is configured.
 * TODO / Integration Required for production: prompt hardening, PII redaction policy, spend limits.
 */
@Slf4j
@Component
public class OpenAiCompatProvider implements AiProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final RestClient http;

    public OpenAiCompatProvider(CrmProperties props) {
        this.apiKey = props.ai().apiKey();
        this.baseUrl = props.ai().baseUrl();
        this.model = props.ai().model();
        this.http = RestClient.create();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result complete(Request request) {
        if (!isAvailable()) return new Result("openai", null, true);
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", request.maxTokens(),
                "messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())));
            ResponseEntity<Map<String, Object>> response = http.post()
                .uri(baseUrl + "/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            if (response.getBody() == null) return new Result("openai", null, true);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) return new Result("openai", null, true);
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return new Result("openai", String.valueOf(message.get("content")), false);
        } catch (Exception e) {
            log.warn("OpenAI-compatible provider failed, falling back: {}", e.getMessage());
            return new Result("openai", null, true);
        }
    }
}
