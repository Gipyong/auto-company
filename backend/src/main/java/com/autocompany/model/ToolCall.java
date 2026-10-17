package com.autocompany.model;

/**
 * 모델이 요청한 도구 호출 (설계 §9.1)
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
