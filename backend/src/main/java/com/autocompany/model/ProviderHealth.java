package com.autocompany.model;

import com.autocompany.entity.ModelProvider;
import java.time.LocalDateTime;

/**
 * provider 상태 확인 결과 (설계 §9.1)
 * status는 ModelProvider.HealthStatus(UNKNOWN/HEALTHY/DEGRADED/UNAVAILABLE)를 재사용한다.
 */
public record ProviderHealth(
        ModelProvider.HealthStatus status,
        String message,
        Long latencyMs,
        LocalDateTime checkedAt
) {
    public static ProviderHealth healthy(long latencyMs) {
        return new ProviderHealth(ModelProvider.HealthStatus.HEALTHY, null, latencyMs, LocalDateTime.now());
    }
}
