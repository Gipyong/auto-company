package com.autocompany.model;

import java.math.BigDecimal;

/**
 * 호출 전 예상 비용 (설계 §9.1, §16)
 *
 * 추정치이므로 estimated=true로 표시하며, 실제 usage 정산(§16)과 혼동하지 않는다.
 */
public record CostEstimate(
        int estimatedInputTokens,
        int estimatedOutputTokens,
        BigDecimal estimatedCost,
        String currency,
        boolean estimated
) {
    public CostEstimate {
        if (estimatedCost == null) {
            throw new IllegalArgumentException("estimatedCost is required");
        }
    }
}
