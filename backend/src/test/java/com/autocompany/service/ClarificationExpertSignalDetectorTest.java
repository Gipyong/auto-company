package com.autocompany.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClarificationExpertSignalDetectorTest {

    private final ClarificationExpertSignalDetector detector = new ClarificationExpertSignalDetector();

    @Test
    void detectsTechStackSignalGranularly() {
        var signals = detector.detect("백엔드는 Spring Boot + PostgreSQL로 부탁합니다");
        assertTrue(signals.stream().anyMatch(s -> s.domain() == ClarificationExpertSignalDetector.Domain.TECH_STACK));
        // 도메인별 세분 신호 — "전문가" 단정이 아님(§28.2.1)
        assertEquals(1, signals.stream().filter(s -> s.domain() == ClarificationExpertSignalDetector.Domain.TECH_STACK).count());
    }

    @Test
    void detectsSecurityAndDesignDomains() {
        var signals = detector.detect("개인정보 암호화 필수. 시안 파일도 첨부합니다.");
        assertTrue(signals.stream().anyMatch(s -> s.domain() == ClarificationExpertSignalDetector.Domain.SECURITY));
        assertTrue(signals.stream().anyMatch(s -> s.domain() == ClarificationExpertSignalDetector.Domain.DESIGN));
    }

    @Test
    void featureExemptionRequiresTwoOrMoreFeatureKeywords() {
        var one = detector.detect("회원가입이 필요합니다");
        var two = detector.detect("회원가입과 댓글 기능이 필요합니다");
        assertFalse(detector.shouldExemptFeatureQuestion(one));
        assertTrue(detector.shouldExemptFeatureQuestion(two));
    }

    @Test
    void blankTextYieldsNoSignals() {
        assertTrue(detector.detect(null).isEmpty());
        assertTrue(detector.detect("   ").isEmpty());
    }
}
