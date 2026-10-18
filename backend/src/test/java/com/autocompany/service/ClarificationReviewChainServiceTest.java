package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-003 검토 체인 단위 테스트(§28.4).
 * 요구사항 체이닝 순서 [PLANNER → ARCHITECT → QA_ENGINEER → SECURITY_DEVOPS(조건부) → PM].
 * 역할 부재는 기록 후 Skip, 통과 실패 시 OPEN 유지, ARCHITECT에서의 기술 스택 노출 차단을 검증 (Rule Mock).
 */
public class ClarificationReviewChainServiceTest {

    private ClarificationQuestionRepository questionRepository;
    private ClarificationSessionRepository sessionRepository;
    private EmployeeRepository employeeRepository;
    private JobGradeRepository jobGradeRepository;
    private DiscussionMessageRepository discussionMessageRepository;
    private ClarificationExpertSignalDetector signalDetector;
    private ClarificationReviewChainService service;

    private UUID sessionId;
    private UUID ownerId;
    private ClarificationSession session;
    private ClarificationQuestion question;

    @BeforeEach
    void setup() {
        questionRepository = mock(ClarificationQuestionRepository.class);
        sessionRepository = mock(ClarificationSessionRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        jobGradeRepository = mock(JobGradeRepository.class);
        discussionMessageRepository = mock(DiscussionMessageRepository.class);
        signalDetector = mock(ClarificationExpertSignalDetector.class);
        service = new ClarificationReviewChainService(questionRepository, sessionRepository,
                employeeRepository, jobGradeRepository, discussionMessageRepository, signalDetector);

        sessionId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        session = ClarificationSession.builder()
                .project(Project.builder()
                        .id(UUID.randomUUID())
                        .user(User.builder().id(ownerId).name("CEO").email("ceo@example.com").build())
                        .name("서비스")
                        .build())
                .goalText("블로그 사이트 만들어줘") // no security keywords
                .sessionNumber(1)
                .status(ClarificationSession.SessionStatus.OPEN)
                .openedByEmployeeId(UUID.randomUUID())
                .build();
        session.setId(sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ClarificationSession.class))).thenAnswer(inv -> inv.getArgument(0));

        question = ClarificationQuestion.builder()
                .session(session)
                .questionType(ClarificationQuestion.QuestionType.SINGLE_CHOICE)
                .prompt("이번 프로젝트의 시작 수준을 알려주세요.")
                .displayOrder(1)
                .allowOther(true)
                .build();
        question.setId(UUID.randomUUID());
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(question));
        when(questionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(discussionMessageRepository.save(any(DiscussionMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(signalDetector.detect(any(String.class))).thenReturn(List.of());
    }

    private void roleExists(Role role, int ordinal) {
        UUID gradeId = UUID.randomUUID();
        Employee e = Employee.builder().name("담당자").gradeId(gradeId).build();
        e.setId(UUID.randomUUID());
        when(employeeRepository.findByUserIdAndRole(ownerId, role)).thenReturn(List.of(e));
        when(jobGradeRepository.findById(gradeId)).thenReturn(Optional.of(JobGrade.builder().id(gradeId).ordinal(ordinal).build()));
    }

    // ==================== 통과 경로 ====================

    @Test
    void chainPassMarksReviewersInOrderAndPromotesToWaitingClient() {
        roleExists(Role.PLANNER, 1);
        roleExists(Role.ARCHITECT, 2);
        roleExists(Role.QA_ENGINEER, 3);
        roleExists(Role.SECURITY_DEVOPS, 4);
        roleExists(Role.PROJECT_MANAGER, 5);
        // 보안 관련 없음 → 감지 결과 비어서 SECURITY 단계 Skip

        boolean passed = service.runReviewChain(sessionId);

        assertTrue(passed);
        assertEquals(4, question.getReviewedByEmployeeIds().size()); // SEC 재정 후 4단계(SEC가 Skip)
        assertEquals(ClarificationSession.SessionStatus.WAITING_CLIENT, session.getStatus()); // §28.4
    }

    @Test
    void chainKeepsWaitingStatusForSecondRun() {
        session.setStatus(ClarificationSession.SessionStatus.WAITING_CLIENT);
        roleExists(Role.PLANNER, 1);

        boolean passed = service.runReviewChain(sessionId);

        assertTrue(passed);
        assertEquals(ClarificationSession.SessionStatus.WAITING_CLIENT, session.getStatus());
    }

    // ==================== 조건부·누락 처리 ====================

    @Test
    void chainSkipsMissingRolesButContinuesToPass() {
        // 전 역할 부재 — "건너뛰고 기록" 후 세션은 PASS
        boolean passed = service.runReviewChain(sessionId);

        assertTrue(passed);
        assertEquals(0, question.getReviewedByEmployeeIds().size());
        assertEquals(ClarificationSession.SessionStatus.WAITING_CLIENT, session.getStatus());
    }

    @Test
    void chainRunsSecurityStepOnlyWhenSecuritySignalPresent() {
        roleExists(Role.PLANNER, 1);
        roleExists(Role.ARCHITECT, 2);
        roleExists(Role.QA_ENGINEER, 3);
        roleExists(Role.SECURITY_DEVOPS, 4);
        roleExists(Role.PROJECT_MANAGER, 5);
        UUID securityEmployeeId = employeeRepository.findByUserIdAndRole(ownerId, Role.SECURITY_DEVOPS).get(0).getId();

        // 보안 관련이 명시되야 평가 단계 진입
        when(signalDetector.detect(any(String.class))).thenReturn(List.of(
                new ClarificationExpertSignalDetector.ExpertSignal(ClarificationExpertSignalDetector.Domain.SECURITY, "개인정보", 1)));

        boolean passed = service.runReviewChain(sessionId);

        assertTrue(passed);
        assertEquals(5, question.getReviewedByEmployeeIds().size()); // §28.4 [4] 보안 단계 관련 시 실행
        assertTrue(question.getReviewedByEmployeeIds().contains(securityEmployeeId));
    }

    // ==================== 반송 경로 (Mock 규칙) ====================

    @Test
    void architectRejectsTechExposedPromptAndRollsBackToOpen() {
        roleExists(Role.PLANNER, 1);
        roleExists(Role.ARCHITECT, 2); // §28.2 아키텍처 격리 — 기술 스택이 질문에 섞여서는 안 됨
        question.setPrompt("React와 PostgreSQL로 API 서버를 구성해달라는 요청 검토");

        boolean passed = service.runReviewChain(sessionId);

        assertFalse(passed);
        assertEquals(ClarificationSession.SessionStatus.OPEN, session.getStatus(), "협의 재검토로 회귀(§13)");
    }
}
