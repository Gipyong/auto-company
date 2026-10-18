package com.autocompany.service;

import com.autocompany.entity.Team;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * §5.3 Team(팀·부서) — 사용자가 자유롭게 생성·이름변경·아카이브하고 직원을 배치한다(TEAM-001).
 * 아카이브는 소속 직원이 있으면 409(직급 삭제와 동일 원칙) — 직원을 옮기거나 미배정으로 바꾼 뒤 가능.
 * 아카이브 팀은 신규 배치 불가(409, active만 허용) — Client.requireActiveForRelay와 동일 패턴.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public Team createTeam(UUID organizationId, String name, String description) {
        if (organizationId == null) {
            throw new IllegalArgumentException("organizationId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String trimmed = name.strip();
        if (teamRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, trimmed)) {
            throw new IllegalStateException("Team with this name already exists: " + trimmed);
        }
        Team team = Team.builder()
                .organizationId(organizationId)
                .name(trimmed)
                .description(description)
                .build();
        return teamRepository.save(team);
    }

    @Transactional(readOnly = true)
    public List<Team> getTeamsByOrganization(UUID organizationId) {
        return teamRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Transactional(readOnly = true)
    public Optional<Team> getTeam(UUID id) {
        return teamRepository.findById(id);
    }

    /** 부분 갱신(PATCH 의미) — null 필드는 유지, 빈 문자열은 지우기(문자열), status=archived는 소속 직원 0명일 때만 */
    @Transactional
    public Team updateTeam(UUID id, String name, String description, String status) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + id));
        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            String trimmed = name.strip();
            if (!trimmed.equalsIgnoreCase(team.getName())
                    && teamRepository.existsByOrganizationIdAndNameIgnoreCase(team.getOrganizationId(), trimmed)) {
                throw new IllegalStateException("Team with this name already exists: " + trimmed);
            }
            team.setName(trimmed);
        }
        if (description != null) {
            team.setDescription(description);
        }
        if (status != null && !status.isBlank()) {
            Team.TeamStatus newStatus;
            try {
                newStatus = Team.TeamStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("status must be active|archived");
            }
            // §5.3 — 아카이브는 소속 직원이 없을 때만(409): 직급 삭제 가드와 동일
            if (newStatus == Team.TeamStatus.ARCHIVED
                    && team.getStatus() != Team.TeamStatus.ARCHIVED
                    && !employeeRepository.findByTeamId(id).isEmpty()) {
                throw new IllegalStateException("Cannot archive team with assigned employees. Reassign employees first.");
            }
            team.setStatus(newStatus);
        }
        return teamRepository.save(team);
    }

    /** 직원 팀 배치 검증 — active 팀만 허용(아카이브 팀 배정 409, §5.3) */
    @Transactional(readOnly = true)
    public Team requireActiveForAssignment(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
        if (team.getStatus() != Team.TeamStatus.ACTIVE) {
            throw new IllegalStateException("Archived team cannot accept new members (§5.3: 409)");
        }
        return team;
    }
}
