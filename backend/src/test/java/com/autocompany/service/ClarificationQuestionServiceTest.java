package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ClarificationQuestionServiceTest {

    private ClarificationQuestionRepository questionRepository;
    private ClarificationSessionRepository sessionRepository;
    private DiscussionMessageRepository discussionMessageRepository;
    private ClarificationQuestionService service;

    private UUID sessionId;
    private UUID projectId;
    private UUID authorId;
    private UUID ownerId;
    private ClarificationSession session;

    @BeforeEach
    void setup() {
        questionRepository = mock(ClarificationQuestionRepository.class);
        sessionRepository = mock(ClarificationSessionRepository.class);
        discussionMessageRepository = mock(DiscussionMessageRepository.class);
        service = new ClarificationQuestionService(questionRepository, sessionRepository,
                discussionMessageRepository, new ClarificationExpertSignalDetector());

        sessionId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        authorId = UUID.randomUUID();
        session = ClarificationSession.builder()
                .project(Project.builder()
                        .id(projectId)
                        .user(User.builder().id(ownerId).name("CEO").email("ceo@example.com").build())
                        .name("서비스")
                        .build())
                .goalText("블로그 사이트 만들어줘")
                .sessionNumber(1)
                .status(SessionStatus.OPEN)
                .openedByEmployeeId(authorId)
                .maxQuestions(5)
                .build();
        session.setId(sessionId); // 실DB에선 개설 시 생성 — 테스트 픽스처는 명시 지정
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ClarificationSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.save(any(ClarificationQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.existsBySessionId(sessionId)).thenReturn(false);
        when(discussionMessageRepository.save(any(DiscussionMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ClarificationQuestion question(int order, ClarificationQuestion.QuestionType type, String... optionValues) {
        List<ClarificationQuestion.QuestionOption> options = java.util.Arrays.stream(optionValues)
                .map(v -> new ClarificationQuestion.QuestionOption(v, v, null))
                .toList();
        ClarificationQuestion q = ClarificationQuestion.builder()
                .questionType(type)
                .prompt("질문 " + order)
                .options(options)
                .allowOther(type != QuestionType.FREE_TEXT) // 템플릿과 동일: 기타(Other)는 자유기술형 제외 모든 유형 제공
                .createdByEmployeeId(authorId)
                .displayOrder(order)
                .build();
        q.setSession(session);
        q.setId(UUID.randomUUID());
        return q;
    }

    // ==================== 구성 ====================

    @Test
    void composeCreatesTemplateQuestionsWithinMaxLimit() {
        List<ClarificationQuestion> result = service.composeQuestions(sessionId);

        assertEquals(5, result.size()); // 템플릿 6개 중 maxQuestions=5 상한
        assertEquals(1, result.get(0).getDisplayOrder());
        assertEquals(QuestionType.SINGLE_CHOICE, result.get(0).getQuestionType());
        assertEquals(SessionStatus.WAITING_CLIENT, session.getStatus());
        verify(questionRepository, times(5)).save(any());
    }

    @Test
    void composeSkipsFeatureQuestionWhenGoalDeclaresFeatures() {
        session.setGoalText("회원가입, 로그인, 댓글 기능이 필요합니다.");

        List<ClarificationQuestion> result = service.composeQuestions(sessionId);

        assertEquals(5, result.size()); // 템플릿 6개 − 기능 1면제(§28.2), 상한 5
        assertTrue(result.stream().noneMatch(q -> q.getQuestionType() == QuestionType.MULTI_CHOICE));
    }

    @Test
    void composeRejectsDuplicateComposition() {
        when(questionRepository.existsBySessionId(sessionId)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.composeQuestions(sessionId));
    }

    @Test
    void composeRejectsNonOpenSession() {
        session.setStatus(SessionStatus.WAITING_CLIENT);
        assertThrows(IllegalStateException.class, () -> service.composeQuestions(sessionId));
    }

    @Test
    void composeMissingSessionIsNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.composeQuestions(sessionId));
    }

    @Test
    void composeRespectsSessionMaxQuestions() {
        session.setMaxQuestions(2);
        List<ClarificationQuestion> result = service.composeQuestions(sessionId);
        assertEquals(2, result.size());
    }

    // ==================== 답변 ====================

    @Test
    void validCompleteAnswersTransitionSessionToReview() {
        ClarificationQuestion q1 = question(1, QuestionType.SINGLE_CHOICE, "mvp", "full");
        ClarificationQuestion q2 = question(2, QuestionType.MULTI_CHOICE, "member", "content");
        ClarificationQuestion q3 = question(3, QuestionType.SHORT_TEXT);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1, q2, q3));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        when(questionRepository.findById(q2.getId())).thenReturn(Optional.of(q2));
        when(questionRepository.findById(q3.getId())).thenReturn(Optional.of(q3));

        session.setStatus(SessionStatus.WAITING_CLIENT);
        ClarificationSession result = service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of("mvp"), null, null, false),
                new ClarificationQuestionService.AnswerItem(q2.getId(), List.of("member", "content"), null, null, false),
                new ClarificationQuestionService.AnswerItem(q3.getId(), List.of(), "두 달 안에 완성", null, false)));

        assertEquals(SessionStatus.REVIEW, result.getStatus());
    }

    @Test
    void partialAnswersThrow409WithRemainingView() {
        ClarificationQuestion q1 = question(1, QuestionType.SINGLE_CHOICE, "mvp", "full");
        ClarificationQuestion q2 = question(2, QuestionType.SHORT_TEXT);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1, q2));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        when(questionRepository.findById(q2.getId())).thenReturn(Optional.of(q2));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        ClarificationQuestionService.RemainingAnswersException e = assertThrows(
                ClarificationQuestionService.RemainingAnswersException.class,
                () -> service.submitAnswers(sessionId, ownerId, List.of(
                        new ClarificationQuestionService.AnswerItem(q1.getId(), List.of("mvp"), null, null, false))));
        assertEquals(1, e.remaining.size());
        assertEquals(q2.getId(), e.remaining.get(0).questionId());
    }

    @Test
    void unknownOptionValueIsRejected() {
        ClarificationQuestion q1 = question(1, QuestionType.SINGLE_CHOICE, "mvp", "full");
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of("vip"), null, null, false))));
    }

    @Test
    void singleChoiceMultipleSelectionsRejected() {
        ClarificationQuestion q1 = question(1, QuestionType.SINGLE_CHOICE, "mvp", "full");
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of("mvp", "full"), null, null, false))));
    }

    @Test
    void singleChoiceOtherTextAcceptedWhenAllowOther() {
        ClarificationQuestion q1 = question(1, QuestionType.SINGLE_CHOICE, "mvp", "full");
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), "두 가지 섞어서", null, false)));

        assertNull(q1.getSelectedValues());
        assertEquals("두 가지 섞어서", q1.getOtherText());
        assertEquals(ClarificationQuestion.AnswerStatus.ANSWERED, q1.getAnswerStatus());
    }

    @Test
    void multiChoiceEmptyWithoutOtherRejected() {
        ClarificationQuestion q1 = question(1, QuestionType.MULTI_CHOICE, "member");
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), null, null, false))));
    }

    @Test
    void freeTextBlankAnswerRejected() {
        ClarificationQuestion q1 = question(1, QuestionType.FREE_TEXT);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), "  ", null, false))));
    }

    @Test
    void numberRangeRequiresNumericAnswer() {
        ClarificationQuestion q1 = question(1, QuestionType.NUMBER_RANGE);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), null, null, false))));

        service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), null, new BigDecimal("100"), false)));
        assertEquals(new BigDecimal("100"), q1.getNumericAnswer());
    }

    @Test
    void skippedAnswerMarkedSkipped() {
        ClarificationQuestion q1 = question(1, QuestionType.SHORT_TEXT);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(q1));
        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(q1.getId(), List.of(), null, null, true)));

        assertEquals(ClarificationQuestion.AnswerStatus.SKIPPED, q1.getAnswerStatus());
    }

    @Test
    void questionFromDifferentSessionRejected() {
        ClarificationQuestion other = question(1, QuestionType.SINGLE_CHOICE, "mvp");
        other.setSession(ClarificationSession.builder()
                .project(Project.builder().id(UUID.randomUUID()).build())
                .goalText("다른 세션")
                .build());
        other.getSession().setId(UUID.randomUUID()); // 실DB 개설 시 생성되는 식별자
        ClarificationQuestion own = question(2, QuestionType.SHORT_TEXT);
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of(own));
        when(questionRepository.findById(other.getId())).thenReturn(Optional.of(other));
        session.setStatus(SessionStatus.WAITING_CLIENT);

        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(other.getId(), List.of("mvp"), null, null, false))));
    }

    @Test
    void frozenSessionRejectsAnswers() {
        session.setStatus(SessionStatus.FROZEN);
        assertThrows(IllegalStateException.class, () -> service.submitAnswers(sessionId, ownerId, List.of()));
    }

    @Test
    void answersWithoutQuestionsRejected() {
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.submitAnswers(sessionId, ownerId, List.of()));
    }

    @Test
    void nonOwnerAnswersRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.submitAnswers(sessionId, UUID.randomUUID(), List.of()));
    }

    @Test
    void unknownQuestionRejected() {
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(
                List.of(question(1, QuestionType.SHORT_TEXT)));
        assertThrows(NoSuchElementException.class, () -> service.submitAnswers(sessionId, ownerId, List.of(
                new ClarificationQuestionService.AnswerItem(UUID.randomUUID(), List.of(), "x", null, false))));
    }
}
