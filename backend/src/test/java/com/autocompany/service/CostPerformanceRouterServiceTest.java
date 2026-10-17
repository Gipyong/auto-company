package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class CostPerformanceRouterServiceTest {

    @Autowired
    private CostPerformanceRouterService routerService;
    @Autowired
    private JobGradeRepository jobGradeRepository;
    @Autowired
    private GradeModelRepository gradeModelRepository;
    @Autowired
    private ModelProfileRepository modelProfileRepository;
    @Autowired
    private ModelProviderRepository modelProviderRepository;

    private ModelProvider provider;
    private ModelProfile lowCostModel;
    private ModelProfile highQualityModel;
    private UUID testGradeId;

    @BeforeEach
    public void setUp() {
        provider = createProvider();

        lowCostModel = createProfile("low-cost-model", 1, null, null);
        highQualityModel = createProfile("high-quality-model", 3, null, null);

        testGradeId = createGrade(JobGrade.ModelSelectionMode.PRIORITY,
                lowCostModel, highQualityModel);
    }

    @Test
    public void testTaskClassification() {
        Task task = Task.builder()
                .type(TaskType.TASK)
                .build();
        CostPerformanceRouterService.TaskClassification classification = routerService.classifyTask(task);
        assertNotNull(classification);
        assertEquals("task", classification.getTaskType());
    }

    @Test
    public void testSelectModelForEmployee() {
        Optional<ModelProfile> selected = routerService.selectModelForEmployee(testGradeId, TaskType.TASK);
        assertTrue(selected.isPresent(), "모델이 선택되어야 합니다.");
        // PRIORITY 모드에서는 우선순위 1인 모델이 선택되어야 함
        assertEquals("low-cost-model", selected.get().getName());
    }

    @Test
    public void testSelectModelRespectsPriority() {
        // 우선순위가 낮은 모델만 enabled=true이면 그것이 선택됨
        Optional<ModelProfile> selected = routerService.selectModelForEmployee(testGradeId, TaskType.TASK);
        assertTrue(selected.isPresent());
        // 첫 번째 모델이 low-cost-model(priority=1)이어야 함
        assertEquals("low-cost-model", selected.get().getName());
    }

    @Test
    public void testSelectModelWithNoGradeModels() {
        UUID emptyGradeId = UUID.randomUUID();
        Optional<ModelProfile> selected = routerService.selectModelForEmployee(emptyGradeId, TaskType.TASK);
        assertFalse(selected.isPresent(), "모델 풀이 없으면 선택되지 않아야 함");
    }

    @Test
    public void testTaskBasedSelectsHighTierForComplexTask() {
        // EPIC: complexity=0.9, qualityReq=0.95 → minTier=3
        UUID gradeId = createGrade(JobGrade.ModelSelectionMode.TASK_BASED,
                lowCostModel, highQualityModel);

        CostPerformanceRouterService.ModelSelectionResult result =
                routerService.selectModelForEmployeeDetailed(gradeId, TaskType.EPIC);

        assertTrue(result.getSelected().isPresent());
        assertEquals("high-quality-model", result.getSelected().get().getName(),
                "복잡한 작업에서는 minTier를 충족하는 고품질 모델이 선택되어야 함");
        assertEquals("TASK_BASED", result.getMode());
    }

    @Test
    public void testTaskBasedPrefersCostEfficientAmongQualityMatched() {
        // 동일 tier(3) 모델 2개, 비용만 다름 → 비용 효율이 좋은(싼) 모델 선택
        ModelProfile expensive = createProfile("expensive-tier3", 3,
                new BigDecimal("10.0"), new BigDecimal("30.0"));
        ModelProfile cheap = createProfile("cheap-tier3", 3,
                new BigDecimal("1.0"), new BigDecimal("2.0"));

        UUID gradeId = createGrade(JobGrade.ModelSelectionMode.TASK_BASED, expensive, cheap);

        CostPerformanceRouterService.ModelSelectionResult result =
                routerService.selectModelForEmployeeDetailed(gradeId, TaskType.EPIC);

        assertTrue(result.getSelected().isPresent());
        assertEquals("cheap-tier3", result.getSelected().get().getName(),
                "품질 요구 충족 후보 중 비용 효율이 가장 좋은 모델이 선택되어야 함");
    }

    @Test
    public void testBudgetOptimizedSelectsCheapestAboveMinTier() {
        // DOCUMENTATION: complexity=0.3, qualityReq=0.8 → minTier=1 (모든 후보 통과)
        ModelProfile expensive = createProfile("expensive-model", 3,
                new BigDecimal("10.0"), new BigDecimal("30.0"));
        ModelProfile cheap = createProfile("cheap-model", 3,
                new BigDecimal("1.0"), new BigDecimal("2.0"));

        UUID gradeId = createGrade(JobGrade.ModelSelectionMode.BUDGET_OPTIMIZED, expensive, cheap);

        CostPerformanceRouterService.ModelSelectionResult result =
                routerService.selectModelForEmployeeDetailed(gradeId, TaskType.DOCUMENTATION);

        assertTrue(result.getSelected().isPresent());
        assertEquals("cheap-model", result.getSelected().get().getName(),
                "minTier 충족 후보 중 최저 비용 모델이 선택되어야 함");
        assertEquals("BUDGET_OPTIMIZED", result.getMode());
        // 선택 이유에 후보 수가 포함되어야 함 (String.format 회귀 방지)
        assertTrue(result.getSelectionReason().contains("후보 2개"),
                "선택 이유에 후보 수가 포함되어야 함: " + result.getSelectionReason());
    }

    @Test
    public void testBudgetOptimizedIgnoresUnknownCost() {
        // 비용 미상 모델은 "무료"가 아니므로 최저 비용 비교에서 제외되어야 함 (설계 §13)
        ModelProfile unknownCost = createProfile("unknown-cost-model", 3, null, null);
        ModelProfile knownCost = createProfile("known-cost-model", 3,
                new BigDecimal("5.0"), new BigDecimal("5.0"));

        UUID gradeId = createGrade(JobGrade.ModelSelectionMode.BUDGET_OPTIMIZED, unknownCost, knownCost);

        CostPerformanceRouterService.ModelSelectionResult result =
                routerService.selectModelForEmployeeDetailed(gradeId, TaskType.DOCUMENTATION);

        assertTrue(result.getSelected().isPresent());
        assertEquals("known-cost-model", result.getSelected().get().getName(),
                "비용 정보가 있는 모델이 비용 미상 모델보다 우선되어야 함");
    }

    @Test
    public void testBudgetOptimizedFallsBackToPriorityWhenAllCostsUnknown() {
        // 모든 후보의 비용이 미상이면 우선순위 순 첫 번째 모델을 폴백 선택
        UUID gradeId = createGrade(JobGrade.ModelSelectionMode.BUDGET_OPTIMIZED,
                lowCostModel, highQualityModel);

        CostPerformanceRouterService.ModelSelectionResult result =
                routerService.selectModelForEmployeeDetailed(gradeId, TaskType.DOCUMENTATION);

        assertTrue(result.getSelected().isPresent());
        assertEquals("low-cost-model", result.getSelected().get().getName(),
                "비용 미상뿐이면 우선순위 첫 번째 모델이 선택되어야 함");
    }

    // ===== Helpers =====

    private ModelProvider createProvider() {
        ModelProvider p = new ModelProvider();
        p.setOrganizationId(UUID.randomUUID());
        p.setName("test-provider");
        p.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);
        p.setEnabled(true);
        p.setHealthStatus(ModelProvider.HealthStatus.HEALTHY);
        return modelProviderRepository.save(p);
    }

    private ModelProfile createProfile(String name, Integer qualityTier,
                                       BigDecimal inputCost, BigDecimal outputCost) {
        ModelProfile profile = new ModelProfile();
        profile.setProvider(provider);
        profile.setName(name);
        profile.setProviderModelId(name);
        profile.setQualityTier(qualityTier);
        profile.setInputCostPerMillion(inputCost);
        profile.setOutputCostPerMillion(outputCost);
        profile.setEnabled(true);
        return modelProfileRepository.save(profile);
    }

    private UUID createGrade(JobGrade.ModelSelectionMode mode, ModelProfile... profiles) {
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(UUID.randomUUID());
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setResponsibilities("기본 구현 작업");
        grade.setModelSelectionMode(mode);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeRepository.save(grade);

        for (int i = 0; i < profiles.length; i++) {
            GradeModel gm = new GradeModel();
            gm.setGradeId(grade.getId());
            gm.setModelProfileId(profiles[i].getId());
            gm.setPriority(i + 1);
            gm.setEnabled(true);
            gradeModelRepository.save(gm);
        }
        return grade.getId();
    }
}
