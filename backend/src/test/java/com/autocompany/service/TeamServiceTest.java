package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.Team;
import com.autocompany.entity.Team.TeamStatus;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** TEAM-001 §5.3 팀(부서) 서비스 — 생성 검증·중복 409·부분 갱신·아카이브 가드·배치 검증 */
public class TeamServiceTest {

    private TeamRepository teamRepository;
    private EmployeeRepository employeeRepository;
    private JobGradeRepository jobGradeRepository;
    private TeamService service;

    @BeforeEach
    void setUp() {
        teamRepository = mock(TeamRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        jobGradeRepository = mock(JobGradeRepository.class);
        service = new TeamService(teamRepository, employeeRepository, jobGradeRepository);
    }

    private Team team(UUID id, TeamStatus status) {
        Team team = Team.builder()
                .organizationId(UUID.randomUUID())
                .name("개발팀")
                .build();
        team.setId(id);
        team.setStatus(status);
        return team;
    }

    @Test
    void createRequiresOrganizationAndName() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createTeam(null, "개발팀", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.createTeam(UUID.randomUUID(), "   ", null));
    }

    @Test
    void createTrimsNameAndStartsActive() {
        UUID organizationId = UUID.randomUUID();
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team created = service.createTeam(organizationId, "  개발팀  ", "제품 개발");

        assertEquals("개발팀", created.getName());
        assertEquals(organizationId, created.getOrganizationId());
        assertEquals(TeamStatus.ACTIVE, created.getStatus()); // §5.3 신규는 active 시작
    }

    @Test
    void createDuplicateNameIsConflict() {
        UUID organizationId = UUID.randomUUID();
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, "개발팀")).thenReturn(true);

        // §5.3 — 조직 내 중복 name 409
        assertThrows(IllegalStateException.class,
                () -> service.createTeam(organizationId, "개발팀", null));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void listIsScopedByOrganization() {
        UUID organizationId = UUID.randomUUID();
        List<Team> rows = List.of(team(UUID.randomUUID(), TeamStatus.ACTIVE));
        when(teamRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)).thenReturn(rows);

        assertEquals(rows, service.getTeamsByOrganization(organizationId));
    }

    @Test
    void updatePatchesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        existing.setDescription("기존 설명");
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(teamRepository.save(existing)).thenReturn(existing);

        Team updated = service.updateTeam(id, "  플랫폼팀 ", null, "active");

        assertEquals("플랫폼팀", updated.getName()); // trim
        assertEquals("기존 설명", updated.getDescription()); // null → 유지
        assertEquals(TeamStatus.ACTIVE, updated.getStatus());
    }

    @Test
    void updateClearsDescriptionOnEmptyString() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        existing.setDescription("기존 설명");
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(teamRepository.save(existing)).thenReturn(existing);

        Team updated = service.updateTeam(id, null, "", null);

        assertEquals("", updated.getDescription()); // 빈 문자열 → 지우기
    }

    @Test
    void updateRejectsBlankNameAndUnknownStatus() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.of(team(id, TeamStatus.ACTIVE)));

        assertThrows(IllegalArgumentException.class, () -> service.updateTeam(id, "   ", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.updateTeam(id, null, null, "deleted"));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateMissingTeamIsNotFound() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.updateTeam(id, "새이름", null, null));
    }

    @Test
    void updateRenameDuplicateIsConflict() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(teamRepository.existsByOrganizationIdAndNameIgnoreCase(existing.getOrganizationId(), "플랫폼팀"))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.updateTeam(id, "플랫폼팀", null, null));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void updateRenameToSameNameIsAllowed() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(teamRepository.save(existing)).thenReturn(existing);

        Team updated = service.updateTeam(id, "개발팀", null, null);

        assertEquals("개발팀", updated.getName()); // 자기 이름 유지 — 중복 검사 제외
    }

    @Test
    void archiveBlockedWhenMembersExist() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.findByTeamId(id)).thenReturn(List.of(Employee.builder().id(UUID.randomUUID()).name("테스트").role(com.autocompany.entity.enums.Role.DEVELOPER).build()));

        // §5.3 — 소속 직원이 있으면 아카이브 409(직급 삭제와 동일 원칙)
        assertThrows(IllegalStateException.class, () -> service.updateTeam(id, null, null, "archived"));
        verify(teamRepository, never()).save(any());
    }

    @Test
    void archiveAllowedWhenEmpty() {
        UUID id = UUID.randomUUID();
        Team existing = team(id, TeamStatus.ACTIVE);
        when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.findByTeamId(id)).thenReturn(List.of());
        when(teamRepository.save(existing)).thenReturn(existing);

        Team updated = service.updateTeam(id, null, null, "archived");

        assertEquals(TeamStatus.ARCHIVED, updated.getStatus());
    }

    @Test
    void requireActiveForAssignmentReturnsActiveTeam() {
        UUID id = UUID.randomUUID();
        Team active = team(id, TeamStatus.ACTIVE);
        when(teamRepository.findById(id)).thenReturn(Optional.of(active));

        assertEquals(active, service.requireActiveForAssignment(id));
    }

    @Test
    void requireActiveForAssignmentRejectsArchived() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.of(team(id, TeamStatus.ARCHIVED)));

        // §5.3 — 아카이브 팀 신규 배치 409
        assertThrows(IllegalStateException.class, () -> service.requireActiveForAssignment(id));
    }

    @Test
    void requireActiveForAssignmentMissingIsNotFound() {
        UUID id = UUID.randomUUID();
        when(teamRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.requireActiveForAssignment(id));
    }
}
