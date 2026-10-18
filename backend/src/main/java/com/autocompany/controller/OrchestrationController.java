package com.autocompany.controller;

import com.autocompany.service.OrchestrationService;
import com.autocompany.service.OrchestrationService.CycleView;
import com.autocompany.service.OrchestrationService.OrchestrationView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 자율 오케스트레이션 API (§17·§29, ORCH-001/002).
 * 트리거는 명시적 — 콘솔 [분해·배정 실행]. 분해·배정은 결정적 Mock이며 결정 근거는 OrchestratorCycle로 기록된다(§5.27).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrchestrationController {

    private final OrchestrationService orchestrationService;

    @PostMapping("/requirements/{requirementSetId}/trigger")
    public ResponseEntity<CycleView> trigger(@PathVariable UUID requirementSetId,
                                             @RequestBody(required = false) TriggerRequest request) {
        UUID userId = request == null ? null : request.userId();
        return ResponseEntity.status(201).body(orchestrationService.trigger(requirementSetId, userId));
    }

    @GetMapping("/projects/{projectId}/orchestration")
    public ResponseEntity<OrchestrationView> summary(@PathVariable UUID projectId) {
        return ResponseEntity.ok(orchestrationService.summary(projectId));
    }

    // ==================== 예외 매핑 ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "ORCHESTRATION_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("error_code", "INVALID_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error_code", "NOT_FOUND", "message", e.getMessage()));
    }

    public record TriggerRequest(UUID userId) {
    }
}
