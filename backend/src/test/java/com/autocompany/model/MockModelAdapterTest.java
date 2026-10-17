package com.autocompany.model;

import com.autocompany.entity.ModelProvider;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MockModelAdapterTest {

    private final MockModelAdapter adapter = new MockModelAdapter();

    @Test
    public void testListModelsReturnsConfiguredDescriptors() {
        List<ModelDescriptor> models = adapter.listModels();

        assertEquals(1, models.size());
        assertEquals(MockModelAdapter.DEFAULT_MODEL_ID, models.get(0).modelId());
        assertTrue(models.get(0).supportsToolCalling());
    }

    @Test
    public void testHealthCheckReturnsConfiguredStatus() {
        assertEquals(ModelProvider.HealthStatus.HEALTHY, adapter.healthCheck().status());

        adapter.setHealthStatus(ModelProvider.HealthStatus.UNAVAILABLE);
        assertEquals(ModelProvider.HealthStatus.UNAVAILABLE, adapter.healthCheck().status());
    }

    @Test
    public void testGenerateReturnsDeterministicResponseWithEstimatedUsage() {
        ModelResponse response = adapter.generate(ModelRequest.of(
                MockModelAdapter.DEFAULT_MODEL_ID,
                List.of(ChatMessage.system("You are a developer."), ChatMessage.user("Fix the bug"))));

        assertEquals(MockModelAdapter.DEFAULT_MODEL_ID, response.modelId());
        assertTrue(response.content().contains("Fix the bug"));
        assertEquals(FinishReason.STOP, response.finishReason());
        assertNotNull(response.providerRequestId());
        assertTrue(response.usage().inputTokens() > 0);
        assertTrue(response.usage().outputTokens() > 0);
        // total_tokens = input + output (설계 §16)
        assertEquals(response.usage().inputTokens() + response.usage().outputTokens(),
                response.usage().totalTokens());
        // mock 생성 usage는 추정값
        assertTrue(response.usage().estimated());
        assertFalse(response.isError());
    }

    @Test
    public void testGenerateReturnsCannedResponseFirst() {
        adapter.enqueueContent("고정 응답입니다");

        ModelResponse first = adapter.generate(ModelRequest.of(
                MockModelAdapter.DEFAULT_MODEL_ID, List.of(ChatMessage.user("질문"))));
        ModelResponse second = adapter.generate(ModelRequest.of(
                MockModelAdapter.DEFAULT_MODEL_ID, List.of(ChatMessage.user("질문"))));

        assertEquals("고정 응답입니다", first.content());
        assertEquals(0, adapter.queuedResponseCount());
        // 큐 소진 후에는 결정적 생성 응답
        assertTrue(second.content().startsWith("[mock:"));
    }

    @Test
    public void testFailureModeThrows() {
        adapter.setFailure(new RuntimeException("timeout 시뮬레이션"));

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                adapter.generate(ModelRequest.of(
                        MockModelAdapter.DEFAULT_MODEL_ID, List.of(ChatMessage.user("질문")))));
        assertEquals("timeout 시뮬레이션", thrown.getMessage());

        adapter.clearFailure();
        assertNotNull(adapter.generate(ModelRequest.of(
                MockModelAdapter.DEFAULT_MODEL_ID, List.of(ChatMessage.user("질문")))));
    }

    @Test
    public void testSimulatedLatency() {
        adapter.setSimulatedLatency(Duration.ofMillis(120));

        long start = System.nanoTime();
        ModelResponse response = adapter.generate(ModelRequest.of(
                MockModelAdapter.DEFAULT_MODEL_ID, List.of(ChatMessage.user("질문"))));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs >= 100, "인위적 지연이 적용되어야 함 (elapsed=" + elapsedMs + "ms)");
        assertTrue(response.latencyMs() >= 100);
    }

    @Test
    public void testEstimateCostUsesConfiguredPricing() {
        adapter.setPricing(new BigDecimal("1.0"), new BigDecimal("2.0"));

        ModelRequest request = new ModelRequest(
                MockModelAdapter.DEFAULT_MODEL_ID,
                List.of(ChatMessage.user("x".repeat(4000))), // ~1000 input tokens
                List.of(), null, Duration.ofSeconds(30), 100, "trace-1");

        CostEstimate estimate = adapter.estimateCost(request);

        assertEquals(1000, estimate.estimatedInputTokens());
        assertEquals(100, estimate.estimatedOutputTokens());
        // 1000/1M*1.0 + 100/1M*2.0 = 0.0012
        assertEquals(0, estimate.estimatedCost().compareTo(new BigDecimal("0.0012")));
        assertEquals("USD", estimate.currency());
        assertTrue(estimate.estimated());
    }

    @Test
    public void testModelRequestValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ModelRequest(
                null, List.of(ChatMessage.user("hi")), List.of(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ModelRequest(
                "m", List.of(), List.of(), null, null, null, null));
    }

    @Test
    public void testUsageValidationAndFactories() {
        assertThrows(IllegalArgumentException.class, () -> Usage.of(-1, 10));

        Usage actual = Usage.of(100, 50);
        assertFalse(actual.estimated());
        assertEquals(150, actual.totalTokens());

        Usage estimated = Usage.estimated(10, 20);
        assertTrue(estimated.estimated());
    }
}
