package com.autocompany.service;

import com.autocompany.entity.Task;
import com.autocompany.entity.TaskType;
import com.autocompany.entity.Employee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Cost-Performance Model Router Engine
 * 
 * 설계서 Section 7에 따른 2단계 선택 시스템:
 * 1. 작업 분류 및 하드 필터링 (Quality Threshold)
 * 2. 비용 대비 효율 최적화 (Cost-Efficiency Score)
 * 3. 실패 시 단계적 승급 (Escalation Chain)
 * 4. 경험 기반 가중치 갱신 (Experience-Based Weight Update)
 */
@Service
public class CostPerformanceRouterService {

    // Model Registry: 모델별 품질/비용/타입 프로필
    private final Map<String, ModelProfile> modelRegistry = new LinkedHashMap<>();
    
    // Task Classification Rules
    private final Map<String, TaskProfile> taskProfiles = new HashMap<>();
    
    // Experiment History (경험 기반 학습)
    private final List<ExperimentRecord> experimentHistory = Collections.synchronizedList(new ArrayList<>());

    // Weights for scoring formula
    private static final float W_QUALITY = 0.4f;
    private static final float W_EXPERIENCE = 0.2f;
    private static final float W_VERIFICATION = 0.1f;
    private static final float W_COST = 0.3f;
    private static final float W_LATENCY = 0.0f;

    public CostPerformanceRouterService(
            @Value("${ai.models.ollama.base-url:http://localhost:11434}") String ollamaUrl,
            @Value("${ai.models.openai.api-key:}") String openaiKey,
            @Value("${ai.models.anthropic.api-key:}") String anthropicKey
    ) {
        initializeModelRegistry();
        initializeTaskProfiles();
    }

    // Model Profile Initialization
    private void initializeModelRegistry() {
        // Local (Free) models
        modelRegistry.put("llama3_local", new ModelProfile(
                "llama3:8b", "local", 0.0f, 0.72f, 2, "ollama"
        ));
        
        // Low-cost API models
        modelRegistry.put("gpt4o_mini", new ModelProfile(
                "gpt-4o-mini", "api", 0.00015f, 0.88f, 1, "openai"
        ));
        
        // High-performance API models
        modelRegistry.put("gpt4o", new ModelProfile(
                "gpt-4o", "api", 0.005f, 0.95f, 1, "openai"
        ));
        
        modelRegistry.put("claude3_5", new ModelProfile(
                "claude-3-5-sonnet-20241022", "api", 0.008f, 0.97f, 1, "anthropic"
        ));
    }

    private void initializeTaskProfiles() {
        taskProfiles.put("simple_fix", new TaskProfile(0.5f, 0.85f, 0.01f, true));
        taskProfiles.put("feature", new TaskProfile(0.8f, 0.90f, 0.05f, false));
        taskProfiles.put("architecture", new TaskProfile(0.9f, 0.95f, 0.10f, false));
        taskProfiles.put("doc", new TaskProfile(0.3f, 0.80f, 0.005f, true));
    }

    /**
     * STEP 1: Task Classification
     */
    public TaskClassification classifyTask(Task task) {
        String taskType = task.getType().name().toLowerCase();
        TaskProfile profile = taskProfiles.getOrDefault(taskType, taskProfiles.get("feature"));
        
        return new TaskClassification(
                taskType,
                profile.complexityScore,
                profile.requiredQuality,
                profile.maxCost,
                profile.deterministicPossible
        );
    }

    /**
     * STEP 2: Hard Filtering - Quality Threshold
     * 품질 기준 미달 모델 즉시 제외
     */
    private List<String> applyQualityFilter(TaskClassification classification) {
        float qualityThreshold = classification.getQualityRequirement();
        return modelRegistry.entrySet().stream()
                .filter(entry -> entry.getValue().qualityScore >= qualityThreshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * STEP 3: Cost-Efficiency Scoring
     * Score = Quality + Experience + Verification - Cost - Latency
     */
    private float calculateScore(ModelProfile model, TaskClassification classification) {
        float qualityFactor = model.qualityScore * W_QUALITY;
        
        ExperienceHistory history = getModelHistory(model.name, classification.getTaskType());
        float experienceFactor = history.successRate * W_EXPERIENCE;
        float verificationFactor = history.verificationRate * W_VERIFICATION;
        
        // Cost: normalize token cost to 0-1 range
        float costFactor = Math.min(model.costPerToken * 10000, 1.0f) * W_COST;
        float latencyFactor = 0.0f; // latency weight = 0
        
        float score = qualityFactor + experienceFactor + verificationFactor - costFactor - latencyFactor;
        return Math.max(0.0f, Math.min(score, 10.0f));
    }

    /**
     * Main Selection: 2단계 선택 시스템
     */
    public String selectBestModel(TaskClassification classification) {
        // 1단계: 품질 필터
        List<String> eligibleModels = applyQualityFilter(classification);
        
        if (eligibleModels.isEmpty()) {
            return null; // No model meets quality threshold
        }
        
        // 2단계: 비용 효율성 최적화 (가장 저렴하면서 품질 만족하는 모델)
        return eligibleModels.stream()
                .max(Comparator.comparingFloat(modelKey -> 
                    calculateScore(modelRegistry.get(modelKey), classification)
                ))
                .orElse(null);
    }

    /**
     * Escalation: 현재 모델이 실패하면 상위 모델로 승급
     */
    public String getEscalationModel(String currentModelKey, String taskType) {
        List<String> models = new ArrayList<>(modelRegistry.keySet());
        int currentIndex = models.indexOf(currentModelKey);
        
        if (currentIndex < 0 || currentIndex >= models.size() - 1) {
            return null; // Already at highest tier
        }
        
        String nextModel = models.get(currentIndex + 1);
        
        // Check if escalation model can meet quality
        TaskClassification testClassification = classifyTask(
                Task.builder().type(TaskType.valueOf(taskType.toUpperCase())).build()
        );
        
        if (calculateScore(modelRegistry.get(nextModel), testClassification) > 0) {
            return nextModel;
        }
        
        return null;
    }

    /**
     * Experience-Based Weight Update
     * 작업 성공/실패 기록을 통해 모델 적합도 자동 갱신
     */
    public void logExperiment(String modelName, String taskType, 
                              boolean success, float cost, boolean verificationPassed) {
        ExperimentRecord record = new ExperimentRecord(
                modelName, taskType, success, cost, verificationPassed, System.currentTimeMillis()
        );
        experimentHistory.add(record);
        
        // Keep last 1000 records
        if (experimentHistory.size() > 1000) {
            experimentHistory.remove(0);
        }
    }

    private ExperienceHistory getModelHistory(String modelName, String taskType) {
        List<ExperimentRecord> relevant = experimentHistory.stream()
                .filter(e -> e.modelName.equals(modelName) && e.taskType.equals(taskType))
                .toList();
        
        if (relevant.isEmpty()) {
            return new ExperienceHistory(0.5f, 0.5f, 0.0f, 0);
        }
        
        float successRate = (float) relevant.stream().filter(ExperimentRecord::success).count() / relevant.size();
        float verificationRate = (float) relevant.stream().filter(ExperimentRecord::verificationPassed).count() / relevant.size();
        float avgCost = relevant.stream().mapToDouble(e -> e.cost).average().orElse(0.0f);
        
        return new ExperienceHistory(successRate, verificationRate, avgCost, relevant.size());
    }

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

    private static class ModelProfile {
        final String name;
        final String type; // "local" or "api"
        final float costPerToken;
        final float qualityScore;
        final int maxRetries;
        final String baseModel;
        
        ModelProfile(String name, String type, float costPerToken, float qualityScore, 
                     int maxRetries, String baseModel) {
            this.name = name;
            this.type = type;
            this.costPerToken = costPerToken;
            this.qualityScore = qualityScore;
            this.maxRetries = maxRetries;
            this.baseModel = baseModel;
        }
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

    private static class ExperimentRecord {
        final String modelName;
        final String taskType;
        final boolean success;
        final float cost;
        final boolean verificationPassed;
        final long timestamp;
        
        ExperimentRecord(String modelName, String taskType, boolean success, 
                         float cost, boolean verificationPassed, long timestamp) {
            this.modelName = modelName;
            this.taskType = taskType;
            this.success = success;
            this.cost = cost;
            this.verificationPassed = verificationPassed;
            this.timestamp = timestamp;
        }
    }

    private static class ExperienceHistory {
        final float successRate;
        final float verificationRate;
        final float avgCost;
        final int totalExperiments;
        
        ExperienceHistory(float successRate, float verificationRate, float avgCost, int totalExperiments) {
            this.successRate = successRate;
            this.verificationRate = verificationRate;
            this.avgCost = avgCost;
            this.totalExperiments = totalExperiments;
        }
    }
}