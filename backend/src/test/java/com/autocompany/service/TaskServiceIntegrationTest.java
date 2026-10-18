package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TaskServiceIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private PeerRatingRepository ratingRepository;
    @Autowired private JobGradeRepository jobGradeRepository;
    @Autowired private TaskTransitionRepository taskTransitionRepository;
    @Autowired private TaskDependencyRepository taskDependencyRepository;

    private User user;
    private Employee assignee;
    private Project project;
    private JobGrade grade;

    @BeforeEach
    public void setUp() {
        user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("ceo@autocompany.com")
                .name("CEO")
                .build());

        grade = new JobGrade();
        grade.setOrganizationId(UUID.randomUUID());
        grade.setCode("L2");
        grade.setName("Mid");
        grade.setOrdinal(2);
        grade.setModelSelectionMode(JobGrade.ModelSelectionMode.PRIORITY);
        grade.setStatus(JobGrade.Status.ACTIVE);
        grade = jobGradeRepository.save(grade);

        assignee = employeeRepository.save(Employee.builder()
                .user(user)
                .name("김개발")
                .role(Role.DEVELOPER)
                .gradeId(grade.getId())
                .status(Status.IDLE)
                .build());

        project = projectRepository.save(Project.builder()
                .user(user)
                .name("테스트 프로젝트")
                .status("PLANNING")
                .build());
    }

    @Test
    public void testAssignTaskPersistsWithTitleAndDescription() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.TASK, "로그인 버그 수정", "Fix null pointer exception");

        assertNotNull(task.getId());
        Task found = taskService.getTask(task.getId()).orElseThrow();
        assertEquals("로그인 버그 수정", found.getTitle());
        assertEquals("Fix null pointer exception", found.getDescription());
        // TASK-001 — 수동 경로도 상태 머신 통과(§7.1): DRAFT→READY→ASSIGNED
        assertEquals(TaskStatus.ASSIGNED, found.getStatus());
        assertEquals(assignee.getId(), found.getAssignee().getId());
        assertEquals(project.getId(), found.getProject().getId());
        assertEquals(List.of(TaskStatus.READY, TaskStatus.ASSIGNED),
                taskTransitionRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                        .stream().map(TaskTransition::getToStatus).toList());
    }

    @Test
    public void testSubmitRatingPersistsRaterAndRatedIds() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.TASK, "t", "p");

        PeerRating rating = taskService.submitRating(task, user.getId(), assignee.getId(),
                "CODE_QUALITY", 4.5f, "좋은 코드입니다", List.of("PR-123", "commit-abc"));

        assertNotNull(rating.getId());
        assertEquals(1, ratingRepository.findByRatedEmployeeId(assignee.getId()).size());
        assertEquals(4.5f, ratingRepository.findByRatedEmployeeId(assignee.getId()).get(0).getScore());
        assertEquals(List.of("PR-123", "commit-abc"), ratingRepository.findByRatedEmployeeId(assignee.getId()).get(0).getEvidenceRefs());
    }

    @Test
    public void testUpdateTaskStatusValidatesTransitionsAndRecordsHistory() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.TASK, "t", "p");

        // §7.1 — ASSIGNED→IN_PROGRESS 유효
        Task updated = taskService.updateTaskStatus(task.getId(), TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, updated.getStatus());

        // §7.1 — IN_PROGRESS→DONE 무효(REVIEW 경유 필요)
        assertThrows(IllegalStateException.class,
                () -> taskService.updateTaskStatus(task.getId(), TaskStatus.DONE));

        // TASK-001 — 모든 전이가 task_transitions에 기록된다
        assertEquals(List.of(TaskStatus.READY, TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS),
                taskTransitionRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                        .stream().map(TaskTransition::getToStatus).toList());
    }

    @Test
    public void testAddDependencyRejectsDuplicateSelfAndCycle() {
        Task a = taskService.assignTask(project.getId(), assignee.getId(), TaskType.TASK, "a", "p");
        Task b = taskService.assignTask(project.getId(), assignee.getId(), TaskType.TEST, "b", "p");

        taskService.addDependency(b.getId(), a.getId(), TaskDependency.DependencyType.BLOCKS);
        assertEquals(1, taskDependencyRepository.findByTaskId(b.getId()).size());

        // 중복 간선 거부
        assertThrows(IllegalStateException.class,
                () -> taskService.addDependency(b.getId(), a.getId(), TaskDependency.DependencyType.BLOCKS));
        // 순환 거부 — a→b를 추가하면 b→a와 직간접 순환
        assertThrows(IllegalStateException.class,
                () -> taskService.addDependency(a.getId(), b.getId(), TaskDependency.DependencyType.BLOCKS));
        // 자기 참조 거부
        assertThrows(IllegalArgumentException.class,
                () -> taskService.addDependency(a.getId(), a.getId(), TaskDependency.DependencyType.BLOCKS));
    }

    @Test
    public void testGetTasksByAssigneeAndProject() {
        taskService.assignTask(project.getId(), assignee.getId(), TaskType.TASK, "t", "p");

        assertEquals(1, taskService.getTasksByAssignee(assignee.getId()).size());
        assertEquals(1, taskService.getTasksByProject(project.getId()).size());
    }

    @Test
    public void testSendMessagePersists() {
        DiscussionMessage message = taskService.sendMessage(project, user.getId(),
                assignee.getId(), "설계 검토 부탁드립니다", "DISCUSSION");

        assertNotNull(message.getId());
        assertEquals(MessageType.DISCUSSION, message.getMessageType());
    }
}