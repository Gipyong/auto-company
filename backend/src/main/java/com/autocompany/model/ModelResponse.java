package com.autocompany.model;

import java.util.List;

/**
 * 모델 호출 응답 (설계 §9.1)
 *
 * @param providerRequestId provider 측 요청 ID (문제 추적용)
 * @param latencyMs         호출 지연 (밀리초)
 */
public record ModelResponse(
        String modelId,
        String content,
        List<ToolCall> toolCalls,
        Usage usage,
        FinishReason finishReason,
        String providerRequestId,
        long latencyMs
) {
    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean isError() {
        return finishReason == FinishReason.ERROR;
    }
}
