package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.TaskType;
import com.autocompany.repository.GradeModelRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ModelProfileRepository;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Model Router using Grade-based Model Pool
 *
 * 선택 로직 (설계 §9.2):
 * 1. 직원 직급(JobGrade)의 가용 모델 풀(GradeModel)을 기준 집합으로 삼음
 * 2. 활성화된 ModelProfile 및 Provider만 후보로 사용
 * 3. 선택 방식(model_selection_mode)에 따라 선택:
 *    - PRIORITY: 직급 내 우선순위 순, 첫 번째 사용 가능 모델
 *    - TASK_BASED: capability 필터링 후 작업 특성 복잡도·품질 요구에 따라 스코어링, 비용 효율 최적 모델 선택
 *    - BUDGET_OPTIMIZED: capability 필터링 후 최소 품질 등급 충족 중 가장 저렴한 모델 선택
 * 4. 선택 이유(ModelSelectionResult.selectionReason) 기록
 */
@Service
public class CostPerformanceRouterService {

    private final JobGradeRepository jobGradeRepository;
    private final GradeModelRepository gradeModelRepository;
    private final ModelProfileRepository modelProfileRepository;

    /** 작업 유형별 필수 capability 매핑 (설계 §9.2) */
    private static final Map<TaskType, Set<String>> REQUIRED_CAPABILITIES = Map.of(
            TaskType.DOCUMENTATION, Set.of("json_mode", "code"),
            TaskType.TEST, Set.of("json_mode", "code"),
            TaskType.BUG, Set.of("code", "reasoning"),
            TaskType.REVIEW, Set.of("code", "reasoning"),
            TaskType.TASK, Set.of("code", "reasoning"),
            TaskType.STORY, Set.of("code", "reasoning", "json_mode"),
            TaskType.SPIKE, Set.of("code", "reasoning", "tool_calling", "json_mode"),
            TaskType.EPIC, Set.of("code", "reasoning", "tool_calling", "json_mode")
    );

    /** 최소 품질 등급 산정 기준 (복잡도 × 품질 요구 점수 → 등급 1~3) */
    private static final int MIN_QUALITY_TIER = 1;

    public CostPerformanceRouterService(JobGradeRepository jobGradeRepository,
                                        GradeModelRepository gradeModelRepository,
                                        ModelProfileRepository modelProfileRepository) {
        this.jobGradeRepository = jobGradeRepository;
        this.gradeModelRepository = gradeModelRepository;
        this.modelProfileRepository = modelProfileRepository;
    }

    /**
     * 직원의 직급에 배정된 모델 풀에서 최적 모델 선택
     * 직급의 model_selection_mode에 따라 PRIORITY / TASK_BASED / BUDGET_OPTIMIZED 전략 사용
     */
    @Transactional(readOnly = true)
    public Optional<ModelProfile> selectModelForEmployee(UUID gradeId, TaskType taskType) {
        return selectModelForEmployeeDetailed(gradeId, taskType).getSelected();
    }

    /**
     * 직원의 직급에 배정된 모델 풀에서 최적 모델 선택 (상세 결과 포함)
     * 선택 이유와 후보 목록을 함께 반환 (설계 §9.2 항목 10)
     */
    @Transactional(readOnly = true)
    public ModelSelectionResult selectModelForEmployeeDetailed(UUID gradeId, TaskType taskType) {
        // 1. 직급의 가용 모델 풀 조회 (우선순위 순)
        List<GradeModel> gradeModels = gradeModelRepository.findByGradeIdAndEnabledTrueOrderByPriority(gradeId);
        if (gradeModels.isEmpty()) {
            return ModelSelectionResult.empty("직급에 가용 모델 풀이 없음 (gradeId=" + gradeId + ")");
        }

        // 2. ModelProfile 조회 및 필터링 (provider 활성화 확인)
        List<ModelProfile> candidates = new ArrayList<>();
        for (GradeModel gm : gradeModels) {
            modelProfileRepository.findById(gm.getModelProfileId()).ifPresent(profile -> {
                if (profile.isEnabled() && profile.getProvider() != null && profile.getProvider().isEnabled()) {
                    candidates.add(profile);
                }
            });
        }

        if (candidates.isEmpty()) {
            return ModelSelectionResult.empty("활성화된 모델 프로필이 없음 (gradeId=" + gradeId + ")");
        }

        // 3. 직급의 model_selection_mode 조회
        JobGrade grade = jobGradeRepository.findById(gradeId).orElse(null);
        JobGrade.ModelSelectionMode mode = grade != null
                ? grade.getModelSelectionMode()
                : JobGrade.ModelSelectionMode.PRIORITY;

        // 4. 선택 모드에 따른 모델 선택
        return switch (mode) {
            case TASK_BASED -> selectModelByTaskType(candidates, taskType);
            case BUDGET_OPTIMIZED -> selectModelByBudget(candidates, taskType);
            case PRIORITY -> selectModelByPriority(candidates, gradeModels);
        };
    }

    /**
     * PRIORITY 모드: 직급 내 우선순위 순, 첫 번째 사용 가능 모델 선택
     */
    private ModelSelectionResult selectModelByPriority(List<ModelProfile> candidates,
                                                         List<GradeModel> gradeModels) {
        ModelProfile selected = candidates.get(0);
        int priority = gradeModels.stream()
                .filter(gm -> gm.getModelProfileId().equals(selected.getId()))
                .map(GradeModel::getPriority)
                .findFirst()
                .orElse(-1);
        String reason = "PRIORITY 모드: 우선순위 #" + priority + " 모델 선택";
        return ModelSelectionResult.of(selected, candidates, reason, "PRIORITY");
    }

    /**
     * TASK_BASED 모드: 작업 특성(복잡도·품질 요구·비용 예산) 기반 스코어링 후 최적 모델 선택
     * - capability 필터링 (작업 유형별 필수 capability)
     * - 품질 등급 기반 후보 필터링
     * - 비용 효율 점수로 스코어링
     */
    private ModelSelectionResult selectModelByTaskType(List<ModelProfile> candidates,
                                                        TaskType taskType) {
        // capability 필터링
        Set<String> requiredCaps = REQUIRED_CAPABILITIES.getOrDefault(taskType,
                Set.of("code"));
        List<ModelProfile> capable = filterByCapability(candidates, requiredCaps);
        if (capable.isEmpty()) {
            // capability를 충족하는 모델이 없으면 전체 후보에서 우선순위 모델 선택
            ModelProfile fallback = candidates.get(0);
            return ModelSelectionResult.of(fallback, candidates,
                    "TASK_BASED: capability(" + requiredCaps + ") 충족 모델 없음, PRIORITY fallback",
                    "TASK_BASED");
        }

        // 작업 분류로부터 품질 요구 산정
        TaskClassification classification = classifyTask(taskType);
        int minQualityTier = deriveMinQualityTier(classification);

        // 품질 등급 필터링
        List<ModelProfile> qualityMatched = capable.stream()
                .filter(p -> (p.getQualityTier() != null && p.getQualityTier() >= minQualityTier))
                .toList();

        if (!qualityMatched.isEmpty()) {
            // 품질 요구 충족 중 비용 효율 가장 좋은 모델 선택
            ModelProfile selected = selectBestValue(qualityMatched);
            String reason = String.format(
                    "TASK_BASED: taskType=%s, complexityScore=%.2f, qualityReq=%.2f, minTier=%d, " +
                    "후보 %d개 중 비용 효율 최적 선택",
                    taskType, classification.getComplexityScore(),
                    classification.getQualityRequirement(), minQualityTier,
                    qualityMatched.size());
            return ModelSelectionResult.of(selected, candidates, reason, "TASK_BASED");
        }

        // 품질 요구를 충족하는 모델이 없으면 가장 높은 품질 모델 선택
        ModelProfile best = capable.stream()
                .max(Comparator.comparing((ModelProfile p) ->
                        p.getQualityTier() != null ? p.getQualityTier() : 0))
                .orElse(capable.get(0));
        String reason = String.format(
                "TASK_BASED: minTier=%d 충족 모델 없음, capability 충족 중 최고 품질 모델 선택 (tier=%s)",
                minQualityTier,
                best.getQualityTier());
        return ModelSelectionResult.of(best, candidates, reason, "TASK_BASED");
    }

    /**
     * BUDGET_OPTIMIZED 모드: 최소 품질 등급 충족 중 가장 저렴한 모델 선택
     */
    private ModelSelectionResult selectModelByBudget(List<ModelProfile> candidates,
                                                      TaskType taskType) {
        // capability 필터링
        Set<String> requiredCaps = REQUIRED_CAPABILITIES.getOrDefault(taskType,
                Set.of("code"));
        List<ModelProfile> capable = filterByCapability(candidates, requiredCaps);
        if (capable.isEmpty()) {
            ModelProfile fallback = candidates.get(0);
            return ModelSelectionResult.of(fallback, candidates,
                    "BUDGET_OPTIMIZED: capability(" + requiredCaps + ") 충족 모델 없음, fallback",
                    "BUDGET_OPTIMIZED");
        }

        // 작업 분류로부터 품질 요구 산정
        TaskClassification classification = classifyTask(taskType);
        int minQualityTier = deriveMinQualityTier(classification);

        // 품질 등급 필터링
        List<ModelProfile> qualityMatched = capable.stream()
                .filter(p -> (p.getQualityTier() != null && p.getQualityTier() >= minQualityTier))
                .toList();

        if (!qualityMatched.isEmpty()) {
            // 품질 요구 충족 중 가장 저렴한 모델 선택
            ModelProfile selected = selectCheapest(qualityMatched);
            String reason = String.format(
                    "BUDGET_OPTIMIZED: taskType=%s, minTier=%d, 후보 %d개 중 최저 비용 선택",
                    taskType, minQualityTier, qualityMatched.size());
            return ModelSelectionResult.of(selected, candidates, reason, "BUDGET_OPTIMIZED");
        }

        // 품질 요구를 충족하는 모델이 없으면 가장 저렴한 모델 선택
        ModelProfile cheapest = selectCheapest(capable);
        String reason = String.format(
                "BUDGET_OPTIMIZED: minTier=%d 충족 모델 없음, capability 충족 중 최저 비용 선택",
                minQualityTier);
        return ModelSelectionResult.of(cheapest, candidates, reason, "BUDGET_OPTIMIZED");
    }

    // ===== Helper Methods =====

    /**
     * capability 필터링: 모델 프로필의 capabilities가 요구사항을 모두 포함하는지 확인
     * capabilities가 null/empty이면 모든 capability를 지원하는 것으로 간주 (하위 호환성)
     */
    private List<ModelProfile> filterByCapability(List<ModelProfile> profiles, Set<String> required) {
        return profiles.stream()
                .filter(p -> {
                    List<String> caps = p.getCapabilities();
                    if (caps == null || caps.isEmpty()) {
                        return true; // capabilities 미지정 시 모든 기능 지원으로 간주
                    }
                    return caps.containsAll(required);
                })
                .collect(Collectors.toList());
    }

    /**
     * 복잡도 × 품질 요구 점수로 최소 품질 등급 산정 (1~3)
     * 설계 §9.2: qualityScore = complexityScore × qualityRequirement (0.0~1.0)
     * 0.00~0.33 → tier 1, 0.34~0.66 → tier 2, 0.67~1.0 → tier 3
     */
    private int deriveMinQualityTier(TaskClassification classification) {
        float qualityScore = classification.getComplexityScore() * classification.getQualityRequirement();
        if (qualityScore >= 0.67f) return 3;
        if (qualityScore >= 0.34f) return 2;
        return MIN_QUALITY_TIER;
    }

    /**
     * 후보 모델들 중 비용 효율(품질 대비 비용)이 가장 좋은 모델 선택
     * 비용이 낮고 품질이 높을수록 점수가 높음
     */
    private ModelProfile selectBestValue(List<ModelProfile> profiles) {
        return profiles.stream()
                .max(Comparator.comparingDouble(this::calculateValueScore))
                .orElse(profiles.get(0));
    }

    /**
     * 후보 모델들 중 가장 저렴한 모델 선택 (비용 기준 오름차순)
     * 비용 정보(input/output cost)가 없는 모델은 비교 대상에서 제외하고,
     * 모든 후보의 비용이 미상이면 우선순위 순 첫 번째 모델을 반환한다.
     */
    private ModelProfile selectCheapest(List<ModelProfile> profiles) {
        return profiles.stream()
                .filter(this::hasKnownCost)
                .min(Comparator.comparing(this::estimatedCost))
                .orElse(profiles.get(0));
    }

    /**
     * 모델의 비용 효율 점수 (품질 등급 / 비용)
     * - 비용이 명시적으로 0이면 무료 모델로 간주하여 높은 점수 부여
     * - 비용 정보가 없는(미상) 모델은 효율 비교가 불가능하므로 낮은 점수 부여 (설계 §13: 로컬 모델 비용을 0으로 간주하지 않음)
     */
    private double calculateValueScore(ModelProfile profile) {
        int tier = profile.getQualityTier() != null ? profile.getQualityTier() : 0;
        if (!hasKnownCost(profile)) {
            return -1.0;
        }
        BigDecimal cost = estimatedCost(profile);
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return tier * 10.0;
        }
        return tier / cost.doubleValue();
    }

    /**
     * 비용 정보(입력/출력 백만 토큰당 비용) 중 하나라도 설정되어 있는지 확인
     */
    private boolean hasKnownCost(ModelProfile profile) {
        return profile.getInputCostPerMillion() != null
                || profile.getOutputCostPerMillion() != null;
    }

    /**
     * 모델의 예상 비용 (input_cost + output_cost)
     * 단위: 백만 토큰 당 비용. 설정되지 않은 항목은 미상이 아니라 0으로 계산 (hasKnownCost 참고)
     */
    private BigDecimal estimatedCost(ModelProfile profile) {
        BigDecimal inputCost = profile.getInputCostPerMillion() != null
                ? profile.getInputCostPerMillion() : BigDecimal.ZERO;
        BigDecimal outputCost = profile.getOutputCostPerMillion() != null
                ? profile.getOutputCostPerMillion() : BigDecimal.ZERO;
        return inputCost.add(outputCost);
    }

    /**
     * 작업 분류 — TaskType 기반 (설계 §9.2)
     */
    public TaskClassification classifyTask(Task task) {
        return classifyTask(task.getType());
    }

    /**
     * TaskType 기반 작업 분류
     */
    public TaskClassification classifyTask(TaskType taskType) {
        String typeKey = taskType.name().toLowerCase();
        TaskProfile profile = taskProfiles.getOrDefault(typeKey, taskProfiles.get("task"));

        return new TaskClassification(
                typeKey,
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

    /**
     * 모델 선택 결과 (설계 §9.2 항목 10: 선택 이유 기록)
     */
    @Data
    public static class ModelSelectionResult {
        private final Optional<ModelProfile> selected;
        private final List<ModelProfile> candidates;
        private final String selectionReason;
        private final String mode;

        public static ModelSelectionResult of(ModelProfile selected, List<ModelProfile> candidates,
                                               String reason, String mode) {
            return new ModelSelectionResult(Optional.of(selected), candidates, reason, mode);
        }

        public static ModelSelectionResult empty(String reason) {
            return new ModelSelectionResult(Optional.empty(), List.of(), reason, "NONE");
        }
    }

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
