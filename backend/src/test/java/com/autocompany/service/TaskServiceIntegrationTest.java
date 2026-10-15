package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

    private User user;
    private Employee assignee;
    private Project project;

    @BeforeEach
    public void setUp() {
        user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email("ceo@autocompany.com")
                .name("CEO")
                .build());

        assignee = employeeRepository.save(Employee.builder()
                .user(user)
                .name("김개발")
                .role(Role.DEVELOPER)
                .rank(Rank.JUNIOR)
                .level(3)
                .build());

        project = projectRepository.save(Project.builder()
                .user(user)
                .name("테스트 프로젝트")
                .status("PLANNING")
                .build());
    }

    @Test
    public void testAssignTaskPersistsWithTitleAndPrompt() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.CODING, "로그인 버그 수정", "Fix null pointer exception");

        assertNotNull(task.getId());
        Task found = taskService.getTask(task.getId()).orElseThrow();
        assertEquals("로그인 버그 수정", found.getTitle());
        assertEquals("Fix null pointer exception", found.getPrompt());
        assertEquals(TaskStatus.PENDING.name(), found.getStatus());
        assertEquals(assignee.getId(), found.getAssignee().getId());
        assertEquals(project.getId(), found.getProject().getId());
    }

    @Test
    public void testSubmitRatingPersistsRaterAndRatedIds() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.CODING, "t", "p");

        PeerRating rating = taskService.submitRating(task, user.getId(), assignee.getId(),
                "CODE_QUALITY", 4.5f, "좋은 코드입니다");

        assertNotNull(rating.getId());
        assertEquals(1, ratingRepository.findByRatedEmployeeId(assignee.getId()).size());
        assertEquals(4.5f, ratingRepository.findByRatedEmployeeId(assignee.getId()).get(0).getScore());
    }

    @Test
    public void testUpdateTaskStatus() {
        Task task = taskService.assignTask(project.getId(), assignee.getId(),
                TaskType.CODING, "t", "p");

        Task updated = taskService.updateTaskStatus(task.getId(), TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS.name(), updated.getStatus());
    }

    @Test
    public void testGetTasksByAssigneeAndProject() {
        taskService.assignTask(project.getId(), assignee.getId(), TaskType.CODING, "t", "p");

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
