package com.autocompany.model;

import com.autocompany.entity.ModelProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트·개발용 Mock 어댑터 (MODEL-001, 설계 §9.1)
 *
 * - 고정 응답 큐, 인위적 지연, 실패 모드, health 상태를 설정할 수 있다
 * - 토큰 수와 비용은 mock 추정치이므로 estimated=true로 표시한다 (실제값과 혼동 금지, §16)
 * - 실제 provider 어댑터는 MODEL-002에서 추가한다
 */
public class MockModelAdapter implements ModelAdapter {

    public static final String DEFAULT_MODEL_ID = "mock-default";
    /** 대략 4자당 1토큰으로 계산하는 mock 추정 규칙 */
    private static final int CHARS_PER_TOKEN = 4;
    private static final int DEFAULT_OUTPUT_TOKEN_ESTIMATE = 500;

    private final List<ModelDescriptor> models;
    private final Queue<ModelResponse> cannedResponses = new ConcurrentLinkedQueue<>();
    private final AtomicLong requestCounter = new AtomicLong();

    private volatile ModelProvider.HealthStatus healthStatus = ModelProvider.HealthStatus.HEALTHY;
    private volatile Duration simulatedLatency = Duration.ZERO;
    private volatile RuntimeException failure;
    private volatile BigDecimal inputCostPerMillion = new BigDecimal("1.0");
    private volatile BigDecimal outputCostPerMillion = new BigDecimal("2.0");

    public MockModelAdapter() {
        this(List.of(new ModelDescriptor(
                DEFAULT_MODEL_ID,
                "Mock Default",
                List.of("code", "reasoning", "json_mode", "tool_calling"),
                8192,
                2048,
                true,
                true)));
    }

    public MockModelAdapter(List<ModelDescriptor> models) {
        if (models == null || models.isEmpty()) {
            throw new IllegalArgumentException("models must not be empty");
        }
        this.models = List.copyOf(models);
    }

    @Override
    public List<ModelDescriptor> listModels() {
        return models;
    }

    @Override
    public ProviderHealth healthCheck() {
        return new ProviderHealth(healthStatus, null, 0L, java.time.LocalDateTime.now());
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        long start = System.nanoTime();
        sleepIfConfigured();

        RuntimeException configuredFailure = failure;
        if (configuredFailure != null) {
            throw configuredFailure;
        }

        ModelResponse canned = cannedResponses.poll();
        if (canned != null) {
            return canned;
        }

        String promptText = request.messages().stream()
                .map(ChatMessage::content)
                .collect(java.util.stream.Collectors.joining("\n"));
        String lastUserContent = request.messages().stream()
                .filter(m -> "user".equals(m.role()))
                .map(ChatMessage::content)
                .reduce((a, b) -> b)
                .orElse("");
        String content = "[mock:" + request.modelId() + "] " + lastUserContent;

        return new ModelResponse(
                request.modelId(),
                content,
                List.of(),
                Usage.estimated(estimateTokens(promptText), estimateTokens(content)),
                FinishReason.STOP,
                "mock-" + requestCounter.incrementAndGet(),
                elapsedMs(start));
    }

    @Override
    public CostEstimate estimateCost(ModelRequest request) {
        String promptText = request.messages().stream()
                .map(ChatMessage::content)
                .collect(java.util.stream.Collectors.joining("\n"));
        int inputTokens = estimateTokens(promptText);
        int outputTokens = request.maxOutputTokens() != null
                ? request.maxOutputTokens()
                : DEFAULT_OUTPUT_TOKEN_ESTIMATE;

        BigDecimal cost = BigDecimal.valueOf(inputTokens)
                .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                .multiply(inputCostPerMillion)
                .add(BigDecimal.valueOf(outputTokens)
                        .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP)
                        .multiply(outputCostPerMillion))
                .stripTrailingZeros();

        return new CostEstimate(inputTokens, outputTokens, cost, "USD", true);
    }

    // ===== 테스트 설정 =====

    /** 다음 generate가 반환할 응답을 큐에 추가 */
    public void enqueueResponse(ModelResponse response) {
        cannedResponses.add(response);
    }

    /** 다음 generate가 반환할 고정 content를 큐에 추가 (usage는 추정값) */
    public void enqueueContent(String content) {
        int outputTokens = estimateTokens(content);
        cannedResponses.add(new ModelResponse(
                DEFAULT_MODEL_ID,
                content,
                List.of(),
                Usage.estimated(0, outputTokens),
                FinishReason.STOP,
                "mock-" + requestCounter.incrementAndGet(),
                0L));
    }

    public int queuedResponseCount() {
        return cannedResponses.size();
    }

    public void setHealthStatus(ModelProvider.HealthStatus healthStatus) {
        this.healthStatus = healthStatus;
    }

    public ModelProvider.HealthStatus getHealthStatus() {
        return healthStatus;
    }

    /** generate 호출 시 인위적으로 지연시킬 시간 */
    public void setSimulatedLatency(Duration simulatedLatency) {
        this.simulatedLatency = simulatedLatency == null ? Duration.ZERO : simulatedLatency;
    }

    /** 설정 시 generate가 해당 예외를 던진다 (timeout/오류 시뮬레이션) */
    public void setFailure(RuntimeException failure) {
        this.failure = failure;
    }

    public void clearFailure() {
        this.failure = null;
    }

    /** 비용 추정에 사용할 mock 단가 설정 (백만 토큰당) */
    public void setPricing(BigDecimal inputCostPerMillion, BigDecimal outputCostPerMillion) {
        this.inputCostPerMillion = inputCostPerMillion;
        this.outputCostPerMillion = outputCostPerMillion;
    }

    // ===== 내부 =====

    private void sleepIfConfigured() {
        Duration latency = simulatedLatency;
        if (latency.isZero()) {
            return;
        }
        try {
            Thread.sleep(latency.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mock latency interrupted", e);
        }
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
