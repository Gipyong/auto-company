package com.autocompany.model;

import java.util.Map;

/**
 * 모델 사용량 (설계 §9.1, §16)
 *
 * - 입력/출력 토큰은 반드시 별도 기록하고 total은 두 값의 합으로 계산한다
 * - provider가 별도 보고하는 필드(cache, reasoning 등)는 raw에 보존한다
 * - 추정값이면 estimated=true로 표시하고 실제값과 혼동하지 않는다
 */
public record Usage(int inputTokens, int outputTokens, boolean estimated, Map<String, Object> raw) {
    public Usage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        raw = raw == null ? Map.of() : Map.copyOf(raw);
    }

    /** total_tokens = 입력 + 출력 (설계 §16) */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    /** 실제 usage (provider 보고값) */
    public static Usage of(int inputTokens, int outputTokens) {
        return new Usage(inputTokens, outputTokens, false, Map.of());
    }

    /** 추정 usage (provider usage 부재 시) */
    public static Usage estimated(int inputTokens, int outputTokens) {
        return new Usage(inputTokens, outputTokens, true, Map.of());
    }
}
