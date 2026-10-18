package com.autocompany.service;

import com.autocompany.entity.Employee;
import com.autocompany.entity.User;
import com.autocompany.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** EMP-001 §5.4 — custom_instructions 검증·부분 갱신·AgentRun 컨텍스트 (3) 계층 조립(§8.2); TEAM-001 §5.3 — 팀 배치 */
public class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private UserService userService;
    private JobGradeService jobGradeService;
    private TeamService teamService;
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        userService = mock(UserService.class);
        jobGradeService = mock(JobGradeService.class);
        teamService = mock(TeamService.class);
        service = new EmployeeService(employeeRepository, userService, jobGradeService, teamService);
    }

    private Employee employee(UUID id) {
        Employee e = Employee.builder().name("김개발").build();
        e.setId(id);
        return e;
    }

    @Test
    void createWithInstructionsTrimsAndPersists() {
        UUID userId = UUID.randomUUID();
        when(userService.getOrCreateLocalUser(userId)).thenReturn(new User());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee created = service.createEmployee(userId, "김개발", "DEVELOPER", "  항상 테스트 먼저 작성  ", null);

        assertEquals("항상 테스트 먼저 작성", created.getCustomInstructions()); // trim 저장
    }

    @Test
    void createNormalizesBlankInstructionsToNull() {
        UUID userId = UUID.randomUUID();
        when(userService.getOrCreateLocalUser(userId)).thenReturn(new User());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee created = service.createEmployee(userId, "김개발", "DEVELOPER", "   ", null);

        assertNull(created.getCustomInstructions()); // 빈 값 = 미설정
    }

    @Test
    void createRejectsInstructionsOverLimit() {
        UUID userId = UUID.randomUUID();
        when(userService.getOrCreateLocalUser(userId)).thenReturn(new User());
        String tooLong = "가".repeat(EmployeeService.MAX_CUSTOM_INSTRUCTIONS_CHARS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.createEmployee(userId, "김개발", "DEVELOPER", tooLong, null));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void createWithActiveTeamAssignsTeam() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(userService.getOrCreateLocalUser(userId)).thenReturn(new User());
        when(teamService.requireActiveForAssignment(teamId))
                .thenReturn(com.autocompany.entity.Team.builder().name("개발팀").build());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        Employee created = service.createEmployee(userId, "김개발", "DEVELOPER", null, teamId);

        assertEquals(teamId, created.getTeamId()); // §5.3 TEAM-001
    }

    @Test
    void createWithArchivedTeamIsConflict() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(userService.getOrCreateLocalUser(userId)).thenReturn(new User());
        when(teamService.requireActiveForAssignment(teamId))
                .thenThrow(new IllegalStateException("Archived team cannot accept new members (§5.3: 409)"));

        assertThrows(IllegalStateException.class,
                () -> service.createEmployee(userId, "김개발", "DEVELOPER", null, teamId));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updatePatchesOnlyProvidedFields() {
        UUID id = UUID.randomUUID();
        Employee existing = employee(id);
        existing.setCustomInstructions("기존 지시");
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee updated = service.updateEmployee(id, "  새이름 ", null, null, false);

        assertEquals("새이름", updated.getName()); // trim
        assertEquals("기존 지시", updated.getCustomInstructions()); // null → 유지
    }

    @Test
    void updateClearsInstructionsOnEmptyString() {
        UUID id = UUID.randomUUID();
        Employee existing = employee(id);
        existing.setCustomInstructions("기존 지시");
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee updated = service.updateEmployee(id, null, "", null, false);

        assertNull(updated.getCustomInstructions()); // 빈 문자열 → 지우기
    }

    @Test
    void updateRejectsBlankNameAndOverLimitInstructions() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee(id)));

        assertThrows(IllegalArgumentException.class, () -> service.updateEmployee(id, "   ", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.updateEmployee(
                id, null, "가".repeat(EmployeeService.MAX_CUSTOM_INSTRUCTIONS_CHARS + 1), null, false));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateMissingEmployeeIsNotFound() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.updateEmployee(id, "이름", null, null, false));
    }

    @Test
    void updateAssignsTeamWhenKeyPresent() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Employee existing = employee(id);
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);
        when(teamService.requireActiveForAssignment(teamId))
                .thenReturn(com.autocompany.entity.Team.builder().name("개발팀").build());

        Employee updated = service.updateEmployee(id, null, null, teamId, true);

        assertEquals(teamId, updated.getTeamId()); // §5.3 키 제시 → 설정
    }

    @Test
    void updateClearsTeamWhenPresentNull() {
        UUID id = UUID.randomUUID();
        Employee existing = employee(id);
        existing.setTeamId(UUID.randomUUID());
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee updated = service.updateEmployee(id, null, null, null, true);

        assertNull(updated.getTeamId()); // 키 제시 + null → 미배정 해제
    }

    @Test
    void updateKeepsTeamWhenKeyAbsent() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Employee existing = employee(id);
        existing.setTeamId(teamId);
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);

        Employee updated = service.updateEmployee(id, "새이름", null, null, false);

        assertEquals(teamId, updated.getTeamId()); // 키 부재 → 유지
    }

    @Test
    void updateUnknownTeamIsNotFound() {
        UUID id = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee(id)));
        when(teamService.requireActiveForAssignment(teamId))
                .thenThrow(new NoSuchElementException("Team not found: " + teamId));

        assertThrows(NoSuchElementException.class, () -> service.updateEmployee(id, null, null, teamId, true));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void buildAgentContextIncludesPromptAndCustomInstructions() {
        UUID id = UUID.randomUUID();
        Employee existing = employee(id);
        existing.setSystemPrompt("직무 원칙: 작은 단위로 커밋한다");
        existing.setCustomInstructions("항상 테스트 먼저 작성한다");
        when(employeeRepository.findById(id)).thenReturn(Optional.of(existing));

        EmployeeService.AgentContext context = service.buildAgentContext(id);

        assertTrue(context.instructions().contains("시스템 안전 정책")); // 안전 정책 우선 문구(§8.2)
        assertTrue(context.instructions().contains("직무 원칙: 작은 단위로 커밋한다"));
        assertTrue(context.instructions().contains("[사용자 개별 지시] 항상 테스트 먼저 작성한다")); // §8.2 (3) 계층
        assertEquals("항상 테스트 먼저 작성한다", context.customInstructions());
    }

    @Test
    void buildAgentContextWithoutInstructionsKeepsSafetyLine() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee(id)));

        EmployeeService.AgentContext context = service.buildAgentContext(id);

        assertNull(context.customInstructions());
        assertTrue(context.instructions().contains("시스템 안전 정책")); // 지시 없음에도 우선 문구 유지
    }

    @Test
    void buildAgentContextMissingEmployeeIsNotFound() {
        UUID id = UUID.randomUUID();
        when(employeeRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.buildAgentContext(id));
    }
}
