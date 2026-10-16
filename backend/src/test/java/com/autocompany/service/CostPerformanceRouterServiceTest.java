package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
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

    private UUID testGradeId;

    @BeforeEach
    public void setUp() {
        // Create a test model provider
        ModelProvider provider = new ModelProvider();
        provider.setOrganizationId(UUID.randomUUID());
        provider.setName("test-provider");
        provider.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);
        provider.setEnabled(true);
        provider.setHealthStatus(ModelProvider.HealthStatus.HEALTHY);
        provider = modelProviderRepository.save(provider);

        // Create test model profiles
        ModelProfile profile1 = new ModelProfile();
        profile1.setProvider(provider);
        profile1.setName("low-cost-model");
        profile1.setProviderModelId("gpt-4o-mini");
        profile1.setQualityTier(1);
        profile1.setEnabled(true);
        profile1 = modelProfileRepository.save(profile1);

        ModelProfile profile2 = new ModelProfile();
        profile2.setProvider(provider);
        profile2.setName("high-quality-model");
        profile2.setProviderModelId("gpt-4o");
        profile2.setQualityTier(3);
        profile2.setEnabled(true);
        profile2 = modelProfileRepository.save(profile2);

        // Create test grade
        JobGrade grade = new JobGrade();
        grade.setOrganizationId(UUID.randomUUID());
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        grade.setResponsibilities("기본 구현 작업");
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeRepository.save(grade);
        testGradeId = grade.getId();

        // Create grade models with priority
        GradeModel gm1 = new GradeModel();
        gm1.setGradeId(testGradeId);
        gm1.setModelProfileId(profile1.getId());
        gm1.setPriority(1);
        gm1.setEnabled(true);
        gradeModelRepository.save(gm1);

        GradeModel gm2 = new GradeModel();
        gm2.setGradeId(testGradeId);
        gm2.setModelProfileId(profile2.getId());
        gm2.setPriority(2);
        gm2.setEnabled(true);
        gradeModelRepository.save(gm2);
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
}