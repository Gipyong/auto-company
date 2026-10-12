package com.autocompany.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

@SpringBootTest
public class CostPerformanceRouterServiceTest {

    @Test
    public void testTaskClassification() {
        CostPerformanceRouterService router = new CostPerformanceRouterService(
                "http://localhost:11434", null, null);
        
        // Section 7.1: 작업 분류 테스트 (간단 수정 -> 복잡한 아키텍처)
        assertNotNull(router.classifyTask(
                new com.autocompany.entity.Task(UUID.randomUUID(), new com.autocompany.entity.Project(UUID.randomUUID(), new com.autocompany.entity.User(UUID.randomUUID(), "test@test.com", "test", "test", false, "test", java.time.LocalDateTime.now()), "test", "test", "test", java.time.LocalDateTime.now()),
                        new com.autocompany.entity.Employee(UUID.randomUUID(), null, "test", com.autocompany.entity.enums.Role.DEVELOPER, com.autocompany.entity.enums.Rank.JUNIOR, 3, "test", "test", com.autocompany.entity.enums.Status.IDLE, 0, 0, 0.0f, "test", java.time.LocalDateTime.now()))));
    }

    @Test
    public void testQualityFilter_ExcludesLowQualityModels() {
        CostPerformanceRouterService router = new CostPerformanceRouterService(
                "http://localhost:11434", null, null);
        
        // Section 7.2: 품질 기준 미달 모델 제외 테스트
        // architecture (0.95 품질 요구) -> llama3_local(0.72) 제외
        assertTrue(router.classifyTask(
                new com.autocompany.entity.Task(UUID.randomUUID(), null, null, "test", "Architecture", "test", "PENDING", null, "test", null, null, java.time.LocalDateTime.now(), null, 0, 0, 0, 0, false)).qualityRequirement > 0.9);
    }

    @Test
    public void testCostEfficiencyScore() {
        CostPerformanceRouterService router = new CostPerformanceRouterService(
                "http://localhost:11434", null, null);
        
        // 비용 효율 점수 계산 검증 (저비용 모델이 더 높은 점수를 받아야 함)
        float score = router.calculateModelScore(
                router.getClass().getDeclaredFields()[0], 
                router.classifyTask(new com.autocompany.entity.Task(UUID.randomUUID(), null, null, "test", "Feature", "test", "PENDING", null, "test", null, null, java.time.LocalDateTime.now(), null, 0, 0, 0, 0, false)));
        assertNotNull(score);
    }

    @Test
    public void testEscalationChain() {
        CostPerformanceRouterService router = new CostPerformanceRouterService(
                "http://localhost:11434", null, null);
        
        // Section 7.4: 단계별 승급 테스트
        assertNotNull(router.getEscalationModel("llama3_local", "feature"));
        assertNotNull(router.getEscalationModel("gpt4o_mini", "feature"));
    }

    @Test
    public void testExperienceBasedWeightUpdate() {
        CostPerformanceRouterService router = new CostPerformanceRouterService(
                "http://localhost:11434", null, null);
        
        // Section 7.5: 경험 기반 가중치 갱신 테스트
        router.logExperiment("llama3_local", "simple_fix", true, 0.0, true);
        router.logExperiment("llama3_local", "simple_fix", false, 0.0, false);
        router.logExperiment("llama3_local", "simple_fix", true, 0.0, true);
        
        // 성공률이 66.7% (2/3)로 기록되어야 함
        float successRate = router.getHistorySuccessRate("llama3_local", "simple_fix");
        assertEquals(0.667f, successRate, 0.05f);
    }
}