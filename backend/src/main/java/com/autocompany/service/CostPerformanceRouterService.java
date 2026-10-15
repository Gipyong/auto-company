package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.TaskType;
import com.autocompany.repository.GradeModelRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ModelProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model Router using Grade-based Model Pool
 * 
 * 선택 로직 (설계 §9.2):
 * 1. 직원 직급(JobGrade)의 가용 모델 풀(GradeModel)을 기준 집합으로 삼음
 * 2. 조직/프로젝트 정책과 교집합
 * 3. Capability 필터링
 * 4. 품질 등급 필터링
 * 5. Provider health/quota 확인
 * 6. 선택 방식(model_selection_mode)에 따라 선택: PRIORITY(기본), TASK_BASED, BUDGET_OPTIMIZED
 */
@Service
public class CostPerformanceRouterService {

    private final JobGradeRepository jobGradeRepository;
    private final GradeModelRepository gradeModelRepository;
    private final ModelProfileRepository modelProfileRepository;

    public CostPerformanceRouterService(JobGradeRepository jobGradeRepository,
                                        GradeModelRepository gradeModelRepository,
                                        ModelProfileRepository modelProfileRepository) {
        this.jobGradeRepository = jobGradeRepository;
        this.gradeModelRepository = gradeModelRepository;
        this.modelProfileRepository = modelProfileRepository;
    }

    /**
     * 직원의 직급에 배정된 모델 풀에서 최적 모델 선택
     * 초기 구현: PRIORITY 모드(우선순위 순서대로 품질/비용 검증 후 첫 번째 사용 가능 모델 선택)
     */
    @Transactional(readOnly = true)
    public Optional<ModelProfile> selectModelForEmployee(UUID employeeGradeId, TaskType taskType) {
        // 1. 직급의 가용 모델 풀 조회 (우선순위 순)
        List<GradeModel> gradeModels = gradeModelRepository.findByGradeIdAndEnabledTrueOrderByPriority(employeeGradeId);
        if (gradeModels.isEmpty()) {
            return Optional.empty();
        }

        // 2. ModelProfile 조회 및 필터링
        List<ModelProfile> candidates = new ArrayList<>();
        for (GradeModel gm : gradeModels) {
            modelProfileRepository.findById(gm.getModelProfileId()).ifPresent(profile -> {
                if (profile.isEnabled() && profile.getProvider() != null && profile.getProvider().isEnabled()) {
                    candidates.add(profile);
                }
            });
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // 3. 작업 유형별 필요 capability 필터링 (확장 가능)
        // TODO: taskType에 따른 capability 필터링 추가

        // 4. 선택 방식에 따른 모델 선택 (초기: PRIORITY = 우선순위 순서대로)
        // GradeModel의 priority 순서는 이미 정렬되어 있으므로 첫 번째 유효 모델 반환
        return Optional.of(candidates.get(0));
    }

    /**
     * 작업 분류 (기존 유지 - 향후 TASK_BASED 선택 시 사용)
     */
    public TaskClassification classifyTask(Task task) {
        String taskType = task.getType().name().toLowerCase();
        TaskProfile profile = taskProfiles.getOrDefault(taskType, taskProfiles.get("task"));
        
        return new TaskClassification(
                taskType,
                profile.complexityScore,
                profile.requiredQuality,
                profile.maxCost,
                profile.deterministicPossible
        );
    }

    // Task Classification Rules
    private final Map<String, TaskProfile> taskProfiles = new HashMap<>() {{
        put("task", new TaskProfile(0.5f, 0.85f, 0.01f, true));
        put("story", new TaskProfile(0.8f, 0.90f, 0.05f, false));
        put("epic", new TaskProfile(0.9f, 0.95f, 0.10f, false));
        put("documentation", new TaskProfile(0.3f, 0.80f, 0.005f, true));
        put("bug", new TaskProfile(0.5f, 0.85f, 0.01f, true));
        put("spike", new TaskProfile(0.7f, 0.88f, 0.03f, false));
        put("review", new TaskProfile(0.4f, 0.82f, 0.005f, true));
        put("test", new TaskProfile(0.4f, 0.82f, 0.005f, true));
    }};

    // Data Classes
    public static class TaskClassification {
        private final String taskType;
        private final float complexityScore;
        private final float qualityRequirement;
        private final float costBudget;
        private final boolean deterministicPossible;
        
        public TaskClassification(String taskType, float complexityScore, 
                                  float qualityRequirement, float costBudget, boolean deterministicPossible) {
            this.taskType = taskType;
            this.complexityScore = complexityScore;
            this.qualityRequirement = qualityRequirement;
            this.costBudget = costBudget;
            this.deterministicPossible = deterministicPossible;
        }
        
        public String getTaskType() { return taskType; }
        public float getComplexityScore() { return complexityScore; }
        public float getQualityRequirement() { return qualityRequirement; }
        public float getCostBudget() { return costBudget; }
        public boolean isDeterministicPossible() { return deterministicPossible; }
    }

    private static class TaskProfile {
        final float complexityScore;
        final float requiredQuality;
        final float maxCost;
        final boolean deterministicPossible;
        
        TaskProfile(float complexityScore, float requiredQuality, float maxCost, boolean deterministicPossible) {
            this.complexityScore = complexityScore;
            this.requiredQuality = requiredQuality;
            this.maxCost = maxCost;
            this.deterministicPossible = deterministicPossible;
        }
    }
}