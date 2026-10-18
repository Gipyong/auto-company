package com.autocompany.controller;

import com.autocompany.entity.TeamFeedback;
import com.autocompany.service.TeamFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 팀 단위 피드백 API (§5.20.1 v1.9) — 팀장이 상대 팀에 제출, 대상은 팀 전체.
 * 콘솔은 읽기 전용 제공, 작성은 팀장만 가능.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamFeedbackController {

    private final TeamFeedbackService teamFeedbackService;

    @PostMapping("/teams/{subjectTeamId}/feedback")
    public ResponseEntity<TeamFeedback> createFeedback(@PathVariable UUID subjectTeamId,
                                                       @RequestBody CreateFeedbackRequest request) {
        TeamFeedback feedback = teamFeedbackService.createFeedback(
                request.organizationId(),
                subjectTeamId,
                request.authorEmployeeId(),
                request.authorTeamId(),
                TeamFeedback.Category.valueOf(request.category()),
                request.score(),
                request.summary(),
                request.evidenceRefs(),
                request.contextWeight()
        );
        return ResponseEntity.status(201).body(feedback);
    }

    @GetMapping("/teams/{teamId}/feedback")
    public ResponseEntity<List<TeamFeedback>> getFeedbackForTeam(@PathVariable UUID teamId) {
        return ResponseEntity.ok(teamFeedbackService.getFeedbackForTeam(teamId));
    }

    @GetMapping("/teams/{teamId}/feedback/summary")
    public ResponseEntity<TeamFeedbackService.TeamFeedbackSummary> getFeedbackSummary(@PathVariable UUID teamId) {
        return ResponseEntity.ok(teamFeedbackService.getFeedbackSummary(teamId));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "TEAM_FEEDBACK_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("error_code", "INVALID_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error_code", "NOT_FOUND", "message", e.getMessage()));
    }

    public record CreateFeedbackRequest(
            UUID organizationId,
            UUID authorEmployeeId,
            UUID authorTeamId,
            String category,
            Float score,
            String summary,
            List<String> evidenceRefs,
            Float contextWeight
    ) {
    }
}