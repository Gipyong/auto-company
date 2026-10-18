package com.autocompany.controller;

import com.autocompany.entity.Team;
import com.autocompany.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 팀(부서) API (§5.3, TEAM-001) — 단일 조직 안에서 사용자가 자유롭게 구성한다.
 * 아카이브는 소속 직원 0명일 때만(409), 아카이브 팀 신규 배치 불가(409).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping("/teams")
    public ResponseEntity<Team> createTeam(@RequestBody TeamRequest request) {
        Team team = teamService.createTeam(request.organizationId, request.name, request.description);
        return ResponseEntity.status(201).body(team);
    }

    @GetMapping("/teams")
    public ResponseEntity<List<Team>> getTeams(@RequestParam UUID organizationId) {
        return ResponseEntity.ok(teamService.getTeamsByOrganization(organizationId));
    }

    @GetMapping("/teams/{id}")
    public ResponseEntity<Team> getTeam(@PathVariable UUID id) {
        return teamService.getTeam(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + id));
    }

    @PatchMapping("/teams/{id}")
    public ResponseEntity<Team> updateTeam(@PathVariable UUID id, @RequestBody TeamPatchRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request.name, request.description, request.status));
    }

    // ==================== 예외 매핑 ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "TEAM_STATE_CONFLICT", "message", e.getMessage()));
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

    public record TeamRequest(UUID organizationId, String name, String description) {
    }

    public record TeamPatchRequest(String name, String description, String status) {
    }
}
