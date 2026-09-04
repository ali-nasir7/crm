package com.crm.modules.ai.service;

/**
 * AI abstraction (§43): the application never binds to a single vendor.
 * Implementations: {@link RuleBasedAiProvider} (deterministic, offline) and
 * {@link OpenAiCompatProvider} (enabled when CRM_AI_API_KEY is set).
 */
public interface AiProvider {

    boolean isAvailable();

    Result complete(Request request);

    record Request(String useCase, String systemPrompt, String userPrompt, int maxTokens) {}

    record Result(String provider, String text, boolean fallbackUsed) {}
}
