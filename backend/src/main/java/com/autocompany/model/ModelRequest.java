package com.autocompany.model;

import java.time.Duration;
import java.util.List;

/**
 * 모델 호출 요청 (설계 §9.1)
 *
 * @param outputJsonSchema JSON schema (null이면 자유 형식 텍스트)
 * @param maxOutputTokens  token budget (null이면 provider 기본값)
 * @param traceId          로그 상관관계용 trace ID
 */
public record ModelRequest(
        String modelId,
        List<ChatMessage> messages,
        List<ToolSpec> tools,
        String outputJsonSchema,
        Duration timeout,
        Integer maxOutputTokens,
        String traceId
) {
    public ModelRequest {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId is required");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public static ModelRequest of(String modelId, List<ChatMessage> messages) {
        return new ModelRequest(modelId, messages, List.of(), null, Duration.ofSeconds(60), null, null);
    }
}
