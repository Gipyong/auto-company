package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Team;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
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
 * §5.3 Team(팀·부서) — 사용자가 자유롭게 생성·이름변경·아카이브·팀 리드 지정하고 직원을 배치한다(TEAM-001, v1.9 팀 운영 모델).
 * 아카이브는 소속 직원이 있으면 409(직급 삭제와 동일 원칙) — 직원을 옮기거나 미배정으로 바꾼 뒤 가능.
 * 아카이브 팀은 신규 배치 불가(409, active만 허용) — Client.requireActiveForRelay와 동일 패턴.
 * 팀장은 **역할**이며 직급이 아니다(`team_lead_employee_id`; 직급 자동 변경 금지 원칙과 무관) — 미지정 팀은 최고 직급(동률 이름순)이 임시 대표로 참여하고 기록에 남긴다.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;

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

    /** 팀 리드 지정/해제(§5.3 v1.9) — 팀원만 가능, 본인 팀 소속 검증 */
    @Transactional
    public Team setTeamLead(UUID teamId, UUID leadEmployeeId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
        if (leadEmployeeId == null) {
            team.setTeamLeadEmployeeId(null);
            return teamRepository.save(team);
        }
        Employee lead = employeeRepository.findById(leadEmployeeId)
                .orElseThrow(() -> new NoSuchElementException("Employee not found: " + leadEmployeeId));
        if (!teamId.equals(lead.getTeamId())) {
            throw new IllegalStateException("Team lead must be a member of the team (§5.3)");
        }
        team.setTeamLeadEmployeeId(leadEmployeeId);
        return teamRepository.save(team);
    }

    /** 팀의 실질 리드 반환(§5.3 v1.9) — 지정 리드 있으면 반환, 없으면 최고 직급(ordinal desc, 동률 이름순) 임시 대표 */
    @Transactional(readOnly = true)
    public Employee getEffectiveTeamLead(UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found: " + teamId));
        if (team.getTeamLeadEmployeeId() != null) {
            return employeeRepository.findById(team.getTeamLeadEmployeeId())
                    .orElseThrow(() -> new NoSuchElementException("Team lead employee not found: " + team.getTeamLeadEmployeeId()));
        }
        List<Employee> members = employeeRepository.findByTeamId(teamId);
        if (members.isEmpty()) {
            throw new IllegalStateException("Team has no members to act as lead: " + teamId);
        }
        // 임시 대표: 최고 직급 ordinal → 이름 순
        return members.stream()
                .filter(e -> e.getGradeId() != null)
                .max((e1, e2) -> {
                    JobGrade g1 = jobGradeRepository.findById(e1.getGradeId()).orElse(null);
                    JobGrade g2 = jobGradeRepository.findById(e2.getGradeId()).orElse(null);
                    int o1 = g1 != null ? g1.getOrdinal() : -1;
                    int o2 = g2 != null ? g2.getOrdinal() : -1;
                    if (o1 != o2) return Integer.compare(o2, o1); // 높은 ordinal 우선
                    return e1.getName().compareTo(e2.getName());
                })
                .orElse(members.stream().min((e1, e2) -> e1.getName().compareTo(e2.getName())).orElseThrow());
    }
}
