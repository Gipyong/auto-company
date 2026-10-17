package com.autocompany.service;

/**
 * 기본 회사 템플릿을 import할 수 없을 때(이미 직급/직원이 존재) 발생한다.
 * 컨트롤러에서 409 Conflict로 매핑된다 (§6.2).
 */
public class TemplateAlreadyImportedException extends RuntimeException {
    public TemplateAlreadyImportedException(String message) {
        super(message);
    }
}
