package com.autocompany.model;

/**
 * 모델 호출 종료 이유 (설계 §9.1)
 */
public enum FinishReason {
    STOP,
    LENGTH,
    TOOL_CALLS,
    CONTENT_FILTER,
    ERROR
}
