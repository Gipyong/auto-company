package com.autocompany.controller;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.QuestionOption;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.RequirementSet;
import com.autocompany.service.ClarificationQuestionService;
import com.autocompany.service.ClarificationQuestionService.RemainingAnswersException;
import com.autocompany.service.ClarificationQuestionService.SubmitAnswersRequest;
import com.autocompany.service.ClarificationSessionService;
import com.autocompany.service.RequirementSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 요구사항 명확화 API (§17, §28) —
 * REQ-001: 골 접수 + 세션 개설·조회·질문 수신함,
 * REQ-002: 질문 구성기 + 구조화 답변(§28.3 최소 질문, §28.4 0단계 신호 선식별),
 * REQ-003: 검토 체인 게이트 + 동결·RequirementSet(§28.6).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClarificationController {

    private final ClarificationSessionService sessionService;
    private final ClarificationQuestionService questionService;
    private final RequirementSetService requirementSetService;

    // ==================== Goal → 세션 개설 ====================

    @PostMapping("/projects/{projectId}/goal")
    public ResponseEntity<SessionResponse> submitGoal(
            @PathVariable UUID projectId,
            @RequestBody GoalRequest request) {
        ClarificationSession session = sessionService.openPipelineSession(
                projectId,
                request.userId(),
                request.goalText(),
                request.attachments() == null ? List.of() : request.attachments().stream()
                        .map(a -> new ClarificationSessionService.AttachmentInput(
                                a.filename(), a.mediaType(), a.sizeBytes(), a.contentBase64(), a.contentUri()))
                        .toList());
        return ResponseEntity.status(201).body(SessionResponse.from(session));
    }

    // ==================== 사용자 개설 세션·취소 (REQ-005, §28.6) ====================

    // 진화 출발점 (b): 사용자가 직접 새 세션 개설 — 변경 취지 원문+첨부
    @PostMapping("/projects/{projectId}/clarification-sessions")
    public ResponseEntity<SessionResponse> openUserSession(
            @PathVariable UUID projectId,
            @RequestBody OpenSessionRequest request) {
        ClarificationSession session = sessionService.openUserSession(
                projectId,
                request.userId(),
                request.changeNote(),
                request.attachments() == null ? List.of() : request.attachments().stream()
                        .map(a -> new ClarificationSessionService.AttachmentInput(
                                a.filename(), a.mediaType(), a.sizeBytes(), a.contentBase64(), a.contentUri()))
                        .toList());
        return ResponseEntity.status(201).body(SessionResponse.from(session));
    }

    @PostMapping("/clarification-sessions/{id}/cancel")
    public ResponseEntity<SessionResponse> cancelSession(@PathVariable UUID id,
                                                         @RequestBody CancelRequest request) {
        return ResponseEntity.ok(SessionResponse.from(sessionService.cancelSession(id, request.userId())));
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

    // ==================== 질문 구성·조회 (REQ-002) ====================

    // 내부 협의 결과로 질문을 준비한다(§28.3) — 체인(REQ-003) 도입 전까지 waiting_client 즉시 진입
    @PostMapping("/clarification-sessions/{id}/questions")
    public ResponseEntity<List<QuestionView>> composeQuestions(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(questionService.composeQuestions(id).stream()
                .map(QuestionView::from)
                .toList());
    }

    @GetMapping("/clarification-sessions/{id}/questions")
    public ResponseEntity<List<QuestionView>> getQuestions(@PathVariable UUID id) {
        return ResponseEntity.ok(questionService.getQuestions(id).stream()
                .map(QuestionView::from)
                .toList());
    }

    // ==================== 답변 접수 (§17: 필수 미답 409 + 남은 질문) ====================

    @PostMapping("/clarification-sessions/{id}/answers")
    public ResponseEntity<SessionResponse> submitAnswers(@PathVariable UUID id,
                                                         @RequestBody SubmitAnswersRequest request) {
        ClarificationSession session = questionService.submitAnswers(id, request.userId(), request.answers());
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ==================== 동결 + RequirementSet (§28.6, REQ-003) ====================

    // 동결 즉시 RequirementSet 생성(불변). frozen은 새 버전만 가능 — 수정 API 없음
    @PostMapping("/clarification-sessions/{id}/freeze")
    public ResponseEntity<RequirementSetResponse> freeze(@PathVariable UUID id,
                                                         @RequestBody FreezeRequest request) {
        RequirementSetService.FrozenOutcome outcome = requirementSetService.freeze(
                id, request.userId(), request.clientDirective());
        return ResponseEntity.status(201).body(RequirementSetResponse.fromRequirements(
                outcome.set(), requirementSetService.parseContent(outcome.set())));
    }

    // 누적 기록(버전별) — §17
    @GetMapping("/projects/{projectId}/requirements")
    public ResponseEntity<List<RequirementSetResponse>> getRequirements(@PathVariable UUID projectId) {
        return ResponseEntity.ok(requirementSetService.listByProject(projectId).stream()
                .map(set -> RequirementSetResponse.fromRequirements(set, requirementSetService.parseContent(set)))
                .toList());
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

    @ExceptionHandler(RemainingAnswersException.class)
    public ResponseEntity<Map<String, Object>> handleRemaining(RemainingAnswersException e) {
        return ResponseEntity.status(409).body(Map.of(
                "error_code", "REQUIRED_ANSWERS_MISSING",
                "message", e.getMessage(),
                "remaining", e.remaining));
    }

    // ==================== DTO ====================

    public record GoalRequest(UUID userId, String goalText, List<AttachmentItem> attachments) {
    }

    /** §28.7(1) 첨부 입력 — MVP는 contentBase64만 지원(§28.7-6) */
    public record AttachmentItem(String filename, String mediaType, Long sizeBytes,
                                 String contentBase64, String contentUri) {
    }

    /** REQ-005 §28.6(b) 사용자 개설 세션 — 개설 취지/변경 요청 원문(불변) 또는 첨부 최소 1개 */
    public record OpenSessionRequest(UUID userId, String changeNote, List<AttachmentItem> attachments) {
    }

    public record CancelRequest(UUID userId) {
    }

    /** clientDirective=true: waiting_client에서 "이 정도면 진행" 명시 지시 동결(§28.6) */
    public record FreezeRequest(UUID userId, Boolean clientDirective) {
    }

    // ===== REQ-003 DTO =====

    public record RequirementItemView(String title, String detail, String source, String evidence, UUID questionId) {
    }

    public record RequirementContentResponse(
            List<RequirementItemView> functional,
            List<RequirementItemView> nonFunctional,
            List<RequirementItemView> constraints,
            List<RequirementItemView> outOfScope,
            List<RequirementItemView> openRisks) {
    }

    public record RequirementSetResponse(
            UUID id,
            UUID projectId,
            UUID sessionId,
            UUID organizationId,
            int version,
            String status,
            List<UUID> frozenByEmployeeIds,
            List<UUID> derivedTaskIds,
            LocalDateTime createdAt,
            LocalDateTime frozenAt,
            RequirementContentResponse content) {

        public static RequirementSetResponse fromRequirements(RequirementSet set, RequirementSetService.RequirementContent c) {
            return new RequirementSetResponse(
                    set.getId(),
                    set.getProject().getId(),
                    set.getSession().getId(),
                    null,
                    set.getVersion(),
                    set.getStatus().name(),
                    set.getFrozenByEmployeeIds() == null ? List.of() : set.getFrozenByEmployeeIds(),
                    set.getDerivedTaskIds() == null ? List.of() : set.getDerivedTaskIds(),
                    set.getCreatedAt(),
                    set.getFrozenAt(),
                    new RequirementContentResponse(
                            map(c.functional()), map(c.nonFunctional()),
                            map(c.constraints()), map(c.outOfScope()), map(c.openRisks())));
        }

        private static List<RequirementItemView> map(List<RequirementSetService.RequirementItem> items) {
            return items == null ? List.of() : items.stream()
                    .map(i -> new RequirementItemView(i.title(), i.detail(), i.source(), i.evidence(), i.questionId()))
                    .toList();
        }
    }

    // ===== REQ-002 DTO =====

    public record QuestionOptionView(String value, String label, String description) {
        public static QuestionOptionView from(QuestionOption o) {
            return new QuestionOptionView(o.value(), o.label(), o.description());
        }
    }

    public record QuestionView(
            UUID id,
            UUID sessionId,
            int displayOrder,
            String questionType,
            String prompt,
            List<QuestionOptionView> options,
            boolean allowOther,
            String answerStatus,
            String otherText,
            List<String> selectedValues,
            BigDecimal numericAnswer) {

        public static QuestionView from(ClarificationQuestion q) {
            return new QuestionView(
                    q.getId(),
                    q.getSession().getId(),
                    q.getDisplayOrder(),
                    q.getQuestionType().name(),
                    q.getPrompt(),
                    q.getOptions() == null ? List.of() :
                            q.getOptions().stream().map(QuestionOptionView::from).toList(),
                    q.isAllowOther(),
                    q.getAnswerStatus().name(),
                    q.getOtherText(),
                    q.getSelectedValues(),
                    q.getNumericAnswer());
        }
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
            List<AttachmentView> attachments,
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
                    s.getAttachments() == null ? List.of() : s.getAttachments().stream()
                            .map(AttachmentView::from)
                            .toList(),
                    s.getCreatedAt());
        }
    }

    /** §28.7-2 세션의 첨부 참조 스냅샷(파싱 상태 포함) */
    public record AttachmentView(UUID artifactId, String mediaType, String filename,
                                 long sizeBytes, String parseStatus) {
        public static AttachmentView from(ClarificationSession.AttachmentRef ref) {
            return new AttachmentView(ref.artifactId(), ref.mediaType(), ref.filename(),
                    ref.sizeBytes(), ref.parseStatus());
        }
    }
}
