package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class OrchestrationServiceTest {

    @Autowired private OrchestrationService orchestrationService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private JobGradeRepository jobGradeRepository;
    @Autowired private ClarificationSessionRepository clarificationSessionRepository;
    @Autowired private RequirementSetRepository requirementSetRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskDependencyRepository taskDependencyRepository;
    @Autowired private TaskTransitionRepository taskTransitionRepository;
    @Autowired private OrchestratorCycleRepository orchestratorCycleRepository;
    @Autowired private ObjectMapper objectMapper;

    private User user;
    private Project project;
    private Employee developer;
    private Employee qa;
    private JobGrade l3;

    @BeforeEach
    public void setUp() {
        user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("orch@autocompany.com")
                .name("발주자")
                .build());
        project = projectRepository.save(Project.builder()
                .user(user)
                .name("주문 관리")
                .status("PLANNING")
                .build());

        JobGrade l2 = jobGradeRepository.save(JobGrade.builder()
                .organizationId(UUID.randomUUID())
                .code("L2").name("주니어").ordinal(2)
                .taskTypes(List.of("TASK", "BUG", "DOCUMENTATION"))
                .modelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY)
                .status(JobGrade.Status.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
        l3 = jobGradeRepository.save(JobGrade.builder()
                .organizationId(UUID.randomUUID())
                .code("L3").name("미들").ordinal(3)
                .taskTypes(List.of("TASK", "BUG", "TEST", "REVIEW", "DOCUMENTATION"))
                .modelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY)
                .status(JobGrade.Status.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        developer = employeeRepository.save(Employee.builder()
                .user(user).name("김개발").role(Role.DEVELOPER)
                .gradeId(l2.getId()).status(Status.IDLE).build());
        qa = employeeRepository.save(Employee.builder()
                .user(user).name("이검증").role(Role.QA_ENGINEER)
                .gradeId(l3.getId()).status(Status.IDLE).build());
    }

    // ==================== ORCH-002: 분해·배정 ====================

    @Test
    public void triggerDecomposesAssignsAndRecordsCycle() {
        RequirementSet set = createFrozenSet(1, content(List.of("주문 등록", "주문 조회")));

        OrchestrationService.CycleView cycle = orchestrationService.trigger(set.getId(), user.getId());

        assertEquals("FIRST", cycle.mode());
        assertEquals(7, cycle.createdTaskCount()); // Epic 1 + 기능 항목당 Story/구현/검증 3
        assertEquals(0, cycle.blockedTaskCount());

        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        assertEquals(7, tasks.size());

        List<Task> epics = tasksByType(tasks, TaskType.EPIC);
        assertEquals(1, epics.size());
        assertEquals(TaskStatus.READY, epics.get(0).getStatus());
        assertNull(epics.get(0).getAssignee());
        assertTrue(epics.get(0).getTitle().contains("v1"));

        List<Task> stories = tasksByType(tasks, TaskType.STORY);
        assertEquals(2, stories.size());
        stories.forEach(s -> {
            assertEquals(TaskStatus.READY, s.getStatus());
            assertNull(s.getAssignee());
            assertEquals(epics.get(0).getId(), s.getParentTask().getId());
        });

        List<Task> implementations = tasks.stream()
                .filter(t -> t.getType() == TaskType.TASK && t.getTitle().startsWith("구현 — ")).toList();
        assertEquals(2, implementations.size());
        implementations.forEach(t -> {
            assertEquals(TaskStatus.ASSIGNED, t.getStatus());
            assertNotNull(t.getAssignee());
            assertEquals(1, taskTransitionRepository.findByTaskIdOrderByCreatedAtAsc(t.getId()).size());
        });

        List<Task> verifications = tasksByType(tasks, TaskType.TEST);
        assertEquals(2, verifications.size());
        verifications.forEach(t -> {
            assertEquals(TaskStatus.ASSIGNED, t.getStatus());
            // TEST는 L3(이검증)만 역량 보유 — 결정적 배정(§29.3)
            assertEquals(qa.getId(), t.getAssignee().getId());
            // 검증은 형제 구현 TASK에 BLOCKS 의존(TEST→TASK, §5.27)
            List<TaskDependency> deps = taskDependencyRepository.findByTaskId(t.getId());
            assertEquals(1, deps.size());
            Task blocked = taskRepository.findById(deps.get(0).getDependsOnTaskId()).orElseThrow();
            assertEquals(TaskType.TASK, blocked.getType());
            assertEquals(t.getParentTask().getId(), blocked.getParentTask().getId());
        });

        // derived_task_ids 연결 → 동일 세트 재분해 방지의 근거
        RequirementSet reloaded = requirementSetRepository.findById(set.getId()).orElseThrow();
        assertEquals(7, reloaded.getDerivedTaskIds().size());
        assertTrue(reloaded.getDerivedTaskIds().contains(epics.get(0).getId()));

        // 사이클 기록(§5.27)
        List<OrchestratorCycle> cycles = orchestratorCycleRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        assertEquals(1, cycles.size());
        assertEquals(OrchestratorCycle.CycleMode.FIRST, cycles.get(0).getMode());
        assertEquals(7, cycles.get(0).getCreatedTaskCount());
        assertEquals(0, cycles.get(0).getBlockedTaskCount());
        assertTrue(cycles.get(0).getDecisionsJson().contains("mock-deterministic"));
    }

    @Test
    public void triggerBlockedWhenNoMatchingEmployee() {
        qa.setStatus(Status.OFFLINE);
        employeeRepository.save(qa);
        RequirementSet set = createFrozenSet(1, content(List.of("주문 등록")));

        OrchestrationService.CycleView cycle = orchestrationService.trigger(set.getId(), user.getId());

        assertEquals(1, cycle.blockedTaskCount());
        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        Task verification = tasksByType(tasks, TaskType.TEST).get(0);
        assertEquals(TaskStatus.BLOCKED, verification.getStatus());
        assertNull(verification.getAssignee());
        assertNotNull(verification.getBlockedReason());
        assertTrue(verification.getBlockedReason().contains("배정 가능한 직원 없음"));
    }

    @Test
    public void triggerCreatesNonFunctionalTaskAndSkipsOutOfScope() {
        RequirementSetService.RequirementContent content = new RequirementSetService.RequirementContent(
                items("주문 등록"), items("응답 2초 이내"), List.of(), items("다국어 지원"),
                List.of(new RequirementSetService.RequirementItem("부하 위험", "피크 시간", "assumption", null, null)));
        RequirementSet set = createFrozenSet(1, content);

        OrchestrationService.CycleView cycle = orchestrationService.trigger(set.getId(), user.getId());

        assertEquals(5, cycle.createdTaskCount()); // Epic 1 + 기능 3 + 비기능 1
        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        List<Task> nonFunctional = tasks.stream()
                .filter(t -> t.getTitle().startsWith("비기능 — ")).toList();
        assertEquals(1, nonFunctional.size());
        assertEquals(TaskStatus.ASSIGNED, nonFunctional.get(0).getStatus());
        assertEquals(TaskType.TASK, nonFunctional.get(0).getType());
        assertTrue(tasksByType(tasks, TaskType.EPIC).get(0).getId().equals(nonFunctional.get(0).getParentTask().getId()));
    }

    // ==================== ORCH-001: 트리거 가드 ====================

    @Test
    public void triggerRejectsNonFrozenSet() {
        ClarificationSession session = clarificationSessionRepository.save(ClarificationSession.builder()
                .project(project).sessionNumber(1)
                .status(ClarificationSession.SessionStatus.OPEN).goalText("골").build());
        RequirementSet draft = requirementSetRepository.save(RequirementSet.builder()
                .organizationId(UUID.randomUUID()).project(project).session(session)
                .version(1).contentJson(toJson(content(List.of("주문 등록"))))
                .status(RequirementSet.SetStatus.DRAFT).build());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> orchestrationService.trigger(draft.getId(), user.getId()));
        assertTrue(e.getMessage().contains("NOT_FROZEN"));
    }

    @Test
    public void triggerRejectsAlreadyDecomposedSet() {
        RequirementSet set = createFrozenSet(1, content(List.of("주문 등록")));
        orchestrationService.trigger(set.getId(), user.getId());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> orchestrationService.trigger(set.getId(), user.getId()));
        assertTrue(e.getMessage().contains("ALREADY_DECOMPOSED"));
        assertEquals(1, orchestratorCycleRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).size());
    }

    @Test
    public void replanModesAndLimit() {
        RequirementSet v1 = createFrozenSet(1, content(List.of("주문 등록")));
        RequirementSet v2 = createFrozenSet(2, content(List.of("주문 등록", "주문 취소")));
        RequirementSet v3 = createFrozenSet(3, content(List.of("주문 등록", "주문 취소", "주문 조회")));
        RequirementSet v4 = createFrozenSet(4, content(List.of("주문 등록")));

        assertEquals("FIRST", orchestrationService.trigger(v1.getId(), user.getId()).mode());
        Map<UUID, TaskStatus> v1Snapshot = taskRepository.findByProjectId(project.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(Task::getId, Task::getStatus));
        assertEquals(4, v1Snapshot.size());

        OrchestrationService.CycleView second = orchestrationService.trigger(v2.getId(), user.getId());
        assertEquals("REPLAN", second.mode());
        assertEquals(2, second.requirementSetVersion());
        assertEquals("REPLAN", orchestrationService.trigger(v3.getId(), user.getId()).mode());

        // 재계획 한도(기본 2회, §23) 초과
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> orchestrationService.trigger(v4.getId(), user.getId()));
        assertTrue(e.getMessage().contains("REPLAN_LIMIT"));
        assertEquals(2, orchestratorCycleRepository.countByProjectIdAndMode(
                project.getId(), OrchestratorCycle.CycleMode.REPLAN));
        // 기존 업무는 변경되지 않는다(§29.2 — Mock 재계획은 신규 분해만)
        v1Snapshot.forEach((taskId, status) ->
                assertEquals(status, taskRepository.findById(taskId).orElseThrow().getStatus()));
        assertEquals(21, taskRepository.findByProjectId(project.getId()).size()); // 4 + 7 + 10
    }

    // ==================== §29.4 조회 ====================

    @Test
    public void summaryReturnsCycleTasksAndLatestFrozen() {
        RequirementSet set = createFrozenSet(1, content(List.of("주문 등록")));

        OrchestrationService.OrchestrationView before = orchestrationService.summary(project.getId());
        assertNull(before.cycle());
        assertTrue(before.tasks().isEmpty());
        assertNotNull(before.latestFrozen());
        assertFalse(before.latestFrozen().decomposed());

        orchestrationService.trigger(set.getId(), user.getId());

        OrchestrationService.OrchestrationView after = orchestrationService.summary(project.getId());
        assertNotNull(after.cycle());
        assertEquals("FIRST", after.cycle().mode());
        assertEquals(4, after.tasks().size());
        OrchestrationService.TaskView epic = after.tasks().stream()
                .filter(t -> "EPIC".equals(t.type())).findFirst().orElseThrow();
        assertNull(epic.assigneeId());
        assertTrue(after.latestFrozen().decomposed());
        assertEquals(1, after.latestFrozen().version());
    }

    @Test
    public void summaryRejectsUnknownProject() {
        assertThrows(NoSuchElementException.class,
                () -> orchestrationService.summary(UUID.randomUUID()));
    }

    // ==================== helpers ====================

    private RequirementSet createFrozenSet(int version, RequirementSetService.RequirementContent content) {
        ClarificationSession session = clarificationSessionRepository.save(ClarificationSession.builder()
                .project(project)
                .sessionNumber(version)
                .status(ClarificationSession.SessionStatus.FROZEN)
                .goalText("골")
                .frozenAt(LocalDateTime.now())
                .build());
        return requirementSetRepository.save(RequirementSet.builder()
                .organizationId(UUID.randomUUID())
                .project(project)
                .session(session)
                .version(version)
                .contentJson(toJson(content))
                .status(RequirementSet.SetStatus.FROZEN)
                .frozenAt(LocalDateTime.now())
                .build());
    }

    private RequirementSetService.RequirementContent content(List<String> functionalTitles) {
        return new RequirementSetService.RequirementContent(
                items(functionalTitles.toArray(new String[0])), List.of(), List.of(), List.of(), List.of());
    }

    private List<RequirementSetService.RequirementItem> items(String... titles) {
        return Arrays.stream(titles)
                .map(t -> new RequirementSetService.RequirementItem(t, t + " 상세", "question", "답변", null))
                .toList();
    }

    private List<Task> tasksByType(List<Task> tasks, TaskType type) {
        return tasks.stream().filter(t -> t.getType() == type).toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
