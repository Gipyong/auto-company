package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.Project;
import com.autocompany.entity.RequirementSet;
import com.autocompany.entity.User;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.RequirementSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-003 동결 단위 테스트(§5.25, §28.6).
 * 동결 == 불변 RequirementSet 생성. version = max+1, 이전 FROZEN → SUPERSEDED 치환,
 * 항목 source 태깅(client_declared/answered/employee_derived), SKIPPED×openRisks(session 가정), 상태 게이트 검증.
 */
public class RequirementSetServiceTest {

    private RequirementSetRepository requirementSetRepository;
    private ClarificationSessionRepository sessionRepository;
    private ClarificationQuestionRepository questionRepository;
    private DiscussionMessageRepository discussionMessageRepository;
    private ClarificationExpertSignalDetector signalDetector;
    private RequirementSetService service;

    private UUID sessionId;
    private UUID projectId;
    private UUID ownerId;
    private ClarificationSession session;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        requirementSetRepository = mock(RequirementSetRepository.class);
        sessionRepository = mock(ClarificationSessionRepository.class);
        questionRepository = mock(ClarificationQuestionRepository.class);
        discussionMessageRepository = mock(DiscussionMessageRepository.class);
        signalDetector = new ClarificationExpertSignalDetector();
        service = new RequirementSetService(requirementSetRepository, sessionRepository,
                questionRepository, discussionMessageRepository, signalDetector, objectMapper);

        sessionId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        session = ClarificationSession.builder()
                .project(Project.builder().id(projectId)
                        .user(User.builder().id(ownerId).name("CEO").email("ceo@example.com").build())
                        .name("서비스").build())
                .goalText("커뮤니티 블로그 사이트 만들기")
                .sessionNumber(1)
                .status(ClarificationSession.SessionStatus.REVIEW)
                .openedByEmployeeId(UUID.randomUUID())
                .build();
        session.setId(sessionId);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ClarificationSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requirementSetRepository.save(any(RequirementSet.class))).thenAnswer(inv -> {
            RequirementSet set = inv.getArgument(0);
            if (set.getId() == null) {
                set.setId(UUID.randomUUID());
            }
            return set;
        });
        when(requirementSetRepository.findByProjectIdOrderByVersionDesc(projectId)).thenReturn(List.of());
    }

    // ---- 문항 픽스처 헬퍼(REQ-002 템플릿 라벨과 동일 언어) ----
    private ClarificationQuestion q(int order, QuestionType type, List<String> selected,
                                    String otherText) {
        ClarificationQuestion.ClarificationQuestionBuilder b = ClarificationQuestion.builder()
                .session(session)
                .questionType(type)
                .prompt("질문 " + order)
                .displayOrder(order)
                .allowOther(type != QuestionType.FREE_TEXT);
        if (selected != null && !selected.isEmpty()) {
            b.options(selected.stream().map(v -> new ClarificationQuestion.QuestionOption(v, v, null)).toList());
        }
        ClarificationQuestion question = b.build();
        question.setId(UUID.randomUUID());
        question.setSelectedValues(selected);
        question.setOtherText(otherText);
        if (otherText != null) {
            question.setAnswerStatus(ClarificationQuestion.AnswerStatus.ANSWERED);
        } else {
            question.setAnswerStatus(ClarificationQuestion.AnswerStatus.ANSWERED);
        }
        return question;
    }

    // ==================== 상태 게이트(§28.6) ====================

    @Test
    void freezeRejectsNotReviewStatus() {
        session.setStatus(ClarificationSession.SessionStatus.WAITING_CLIENT);
        assertThrows(IllegalStateException.class, () -> service.freeze(sessionId, ownerId));
    }

    @Test
    void freezeRejectsFrozenSessionWithNewSessionGuidance() {
        session.setStatus(ClarificationSession.SessionStatus.FROZEN);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.freeze(sessionId, ownerId));
        assertTrue(e.getMessage().contains("new session")); // 진화 경로 유도(REQ-005)
    }

    @Test
    void freezeRejectsCancelledSession() {
        session.setStatus(ClarificationSession.SessionStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> service.freeze(sessionId, ownerId));
    }

    @Test
    void freezeRejectsNonOwner() {
        assertThrows(IllegalArgumentException.class, () -> service.freeze(sessionId, UUID.randomUUID()));
    }

    @Test
    void freezeRejectsRemainingUnansweredQuestions() {
        ClarificationQuestion answered = q(1, QuestionType.SINGLE_CHOICE, List.of("mvp"), null);
        ClarificationQuestion unanswered = ClarificationQuestion.builder()
                .session(session).questionType(QuestionType.SHORT_TEXT)
                .prompt("기한").displayOrder(2).build();
        unanswered.setId(UUID.randomUUID());
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(answered, unanswered));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.freeze(sessionId, ownerId));
        assertTrue(e.getMessage().contains("Unanswered")); // 동결 전 필수 답변 완료 요구
    }

    @Test
    void freezeRejectsMissingSession() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.freeze(sessionId, ownerId));
    }

    // ==================== 동결 성공 — content 매핑 ====================

    @Test
    void freezeCreatesFirstVersionAsFrozenAndSessionFrozen() {
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(
                q(1, QuestionType.SINGLE_CHOICE, List.of("mvp"), null),
                q(2, QuestionType.MULTI_CHOICE, List.of("member"), null),
                q(3, QuestionType.SHORT_TEXT, null, "두 달 안에 완성")));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertEquals(1, outcome.set().getVersion());
        assertEquals(RequirementSet.SetStatus.FROZEN, outcome.set().getStatus());
        assertEquals(ClarificationSession.SessionStatus.FROZEN, session.getStatus());
        assertEquals(sessionId, outcome.set().getSession().getId());

        RequirementSetService.RequirementContent content = outcome.content();
        assertEquals("client_declared", content.functional().get(0).source()); // goal → 초기 목표
        assertEquals("client_answered", content.functional().get(1).source()); // 시작수준 확정
        assertEquals(1, content.functional().stream()
                .filter(i -> i.source().equals("client_answered") && i.title().equals("기능")).count());
        assertTrue(content.constraints().stream().anyMatch(i -> i.detail().contains("두 달 안에 완성")));
        assertEquals("employee_derived", content.outOfScope().get(0).source()); // MVP → 미해결 확장
    }

    @Test
    void freezeBumpsVersionAndSupersedesPreviousFrozenSet() {
        RequirementSet previous = RequirementSet.builder()
                .project(session.getProject()).session(session)
                .version(2).status(RequirementSet.SetStatus.FROZEN)
                .contentJson("{}").build();
        previous.setId(UUID.randomUUID());
        when(requirementSetRepository.findByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(List.of(previous));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(q(1, QuestionType.SINGLE_CHOICE, List.of("full"), null)));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertEquals(3, outcome.set().getVersion()); // max+1 (§5.25 진화)
        assertEquals(RequirementSet.SetStatus.SUPERSEDED, previous.getStatus()); // 이전 FROZEN 치환
    }

    @Test
    void freezeRecordsSkippedQuestionAssumptionAsEmployeeDerivedRisk() {
        ClarificationQuestion skipped = ClarificationQuestion.builder()
                .session(session).questionType(QuestionType.SHORT_TEXT)
                .prompt("기한")
                .displayOrder(1)
                .assumptionIfSkipped("기한 미확정 — 추후 협의")
                .build();
        skipped.setId(UUID.randomUUID());
        skipped.setAnswerStatus(ClarificationQuestion.AnswerStatus.SKIPPED);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(skipped));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertEquals("employee_derived", outcome.content().openRisks().get(0).source()); // §5.24 SKIPPED 기록만 유효
        assertTrue(outcome.content().openRisks().get(0).detail().contains("추후 협의"));
    }

    @Test
    void freezeRecordsSignalsAsDeclaredConstraints() {
        session.setGoalText("문서 암호화와 개인정보 보호가 필요한 블로그 사이트"); // SECURITY/BUDGET 신호
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of());

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertEquals(1, outcome.content().nonFunctional().size()); // 보안 요건 client_declared
        RequirementSetService.RequirementItem item = outcome.content().nonFunctional().get(0);
        assertEquals("client_declared", item.source());
        assertTrue(item.detail().contains("암호화"));
    }
}
