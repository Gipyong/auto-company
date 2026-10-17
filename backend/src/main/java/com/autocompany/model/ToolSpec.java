package com.autocompany.model;

/**
 * 모델에 전달하는 도구(tool) 스키마 (설계 §9.1)
 */
public record ToolSpec(String name, String description, String parametersJsonSchema) {
}
