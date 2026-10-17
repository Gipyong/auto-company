package com.autocompany.model;

import java.util.List;

/**
 * provider가 제공하는 모델 설명 (설계 §9.1)
 */
public record ModelDescriptor(
        String modelId,
        String displayName,
        List<String> capabilities,
        Integer contextWindow,
        Integer maxOutputTokens,
        boolean supportsToolCalling,
        boolean supportsJsonSchema
) {
    public ModelDescriptor {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
