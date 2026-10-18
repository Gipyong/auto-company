package com.autocompany.controller;

import com.autocompany.entity.ClarificationSession;
import com.autocompany.service.ClarificationSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 요구사항 명확화 API (§17, §28) — REQ-001: 골 접수 + 세션 개설·조회·질문 수신함.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClarificationController {

    private final ClarificationSessionService sessionService;

    // ==================== Goal → 세션 개설 ====================

    @PostMapping("/projects/{projectId}/goal")
    public ResponseEntity<SessionResponse> submitGoal(
            @PathVariable UUID projectId,
            @RequestBody GoalRequest request) {
        ClarificationSession session = sessionService.openPipelineSession(projectId, request.userId(), request.goalText());
        return ResponseEntity.status(201).body(SessionResponse.from(session));
    }

    // ==================== 세션 조회 ====================

    @GetMapping("/projects/{projectId}/clarification-sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(@PathVariable UUID projectId) {
        return ResponseEntity.ok(sessionService.getSessionsByProject(projectId).stream()
                .map(SessionResponse::from)
                .toList());
    }

    @GetMapping("/clarification-sessions/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable UUID id) {
        return sessionService.getSession(id)
                .map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Clarification session not found: " + id));
    }

    // ==================== 질문 수신함 (§28.5) ====================

    @GetMapping("/users/{userId}/pending-sessions")
    public ResponseEntity<List<SessionResponse>> getPendingSessions(@PathVariable UUID userId) {
        return ResponseEntity.ok(sessionService.getPendingSessionsForUser(userId).stream()
                .map(SessionResponse::from)
                .toList());
    }

    // ==================== 예외 매핑 ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "SESSION_STATE_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("error_code", "INVALID_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error_code", "NOT_FOUND", "message", e.getMessage()));
    }

    // ==================== DTO ====================

    public record GoalRequest(UUID userId, String goalText) {
    }

    public record SessionResponse(
            UUID id,
            UUID projectId,
            String goalText,
            int sessionNumber,
            String status,
            UUID openedByEmployeeId,
            UUID openedByUserId,
            UUID clientId,
            String clientExtraNote,
            int maxQuestions,
            UUID parentSessionId,
            LocalDateTime createdAt) {

        public static SessionResponse from(ClarificationSession s) {
            return new SessionResponse(
                    s.getId(),
                    s.getProject().getId(),
                    s.getGoalText(),
                    s.getSessionNumber(),
                    s.getStatus().name(),
                    s.getOpenedByEmployeeId(),
                    s.getOpenedByUserId(),
                    s.getClientId(),
                    s.getClientExtraNote(),
                    s.getMaxQuestions(),
                    s.getParentSessionId(),
                    s.getCreatedAt());
        }
    }
}
