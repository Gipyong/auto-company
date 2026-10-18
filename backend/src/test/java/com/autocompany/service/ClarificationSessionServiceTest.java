package com.autocompany.service;

import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class ClarificationSessionServiceTest {

    private ClarificationSessionRepository sessionRepository;
    private ProjectRepository projectRepository;
    private EmployeeRepository employeeRepository;
    private JobGradeRepository jobGradeRepository;
    private DiscussionMessageRepository discussionMessageRepository;
    private ClarificationSessionService service;

    private UUID userId;
    private UUID projectId;
    private Project project;
    private Employee planner;
    private JobGrade leadGrade;

    @BeforeEach
    void setup() {
        sessionRepository = mock(ClarificationSessionRepository.class);
        projectRepository = mock(ProjectRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        jobGradeRepository = mock(JobGradeRepository.class);
        discussionMessageRepository = mock(DiscussionMessageRepository.class);
        service = new ClarificationSessionService(
                sessionRepository, projectRepository, employeeRepository, jobGradeRepository, discussionMessageRepository);

        userId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        User user = User.builder().id(userId).name("CEO").email("ceo@example.com").build();
        project = Project.builder().id(projectId).user(user).name("블로그 사이트").build();
        leadGrade = JobGrade.builder().id(UUID.randomUUID()).code("L4").name("Lead").ordinal(4).build();
        planner = Employee.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("기획자")
                .role(Role.PLANNER)
                .gradeId(leadGrade.getId())
                .build();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(sessionRepository.existsByProjectIdAndStatusIn(any(), anyList())).thenReturn(false);
        when(sessionRepository.findFirstByProjectIdOrderBySessionNumberDesc(projectId)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ClarificationSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(discussionMessageRepository.save(any(DiscussionMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void goalSubmitCreatesFirstOpenSession() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openPipelineSession(projectId, userId, "블로그 사이트 만들어줘");

        assertEquals(1, session.getSessionNumber());
        assertEquals(SessionStatus.OPEN, session.getStatus());
        assertEquals(planner.getId(), session.getOpenedByEmployeeId());
        assertNull(session.getParentSessionId());
        verify(sessionRepository).save(any(ClarificationSession.class));
        verify(discussionMessageRepository).save(any(DiscussionMessage.class));
    }

    @Test
    void newSessionEvolutionLinksParentAndIncrementsNumber() {
        ClarificationSession previous = ClarificationSession.builder()
                .project(project)
                .goalText("1차 골")
                .sessionNumber(1)
                .status(SessionStatus.FROZEN)
                .openedByEmployeeId(planner.getId())
                .createdAt(LocalDateTime.now())
                .build();
        when(sessionRepository.findFirstByProjectIdOrderBySessionNumberDesc(projectId))
                .thenReturn(Optional.of(previous));
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openPipelineSession(projectId, userId, "댓글 기능 추가요");

        assertEquals(2, session.getSessionNumber());
        assertEquals(previous.getId(), session.getParentSessionId());
    }

    @Test
    void activeSessionConflictReturns409State() {
        when(sessionRepository.existsByProjectIdAndStatusIn(projectId, List.of(SessionStatus.OPEN, SessionStatus.WAITING_CLIENT)))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.openPipelineSession(projectId, userId, "블로그 사이트 만들어줘"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void blankGoalTextIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openPipelineSession(projectId, userId, "   "));
    }

    @Test
    void nonOwnerUserIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openPipelineSession(projectId, UUID.randomUUID(), "블로그 사이트 만들어줘"));
    }

    @Test
    void nullUserIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openPipelineSession(projectId, null, "블로그 사이트 만들어줘"));
    }

    @Test
    void unknownProjectIsNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> service.openPipelineSession(projectId, userId, "블로그 사이트 만들어줘"));
    }

    @Test
    void sessionWithoutPlannerEmployeeIsRejected() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,
                () -> service.openPipelineSession(projectId, userId, "블로그 사이트 만들어줘"));
    }
}
