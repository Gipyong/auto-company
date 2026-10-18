package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.Client;
import com.autocompany.entity.Project;
import com.autocompany.entity.RequirementSet;
import com.autocompany.entity.User;
import com.autocompany.repository.ArtifactRepository;
import com.autocompany.repository.ClientRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    private ArtifactRepository artifactRepository;
    private ClarificationExpertSignalDetector signalDetector;
    private ClarificationReviewChainService reviewChainService;
    private ClientRepository clientRepository;
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
        artifactRepository = mock(ArtifactRepository.class);
        signalDetector = new ClarificationExpertSignalDetector();
        reviewChainService = mock(ClarificationReviewChainService.class);
        clientRepository = mock(ClientRepository.class);
        service = new RequirementSetService(requirementSetRepository, sessionRepository,
                questionRepository, discussionMessageRepository, signalDetector, objectMapper, artifactRepository,
                reviewChainService, clientRepository);

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

    @Test
    void freezeRecordsAttachmentSignalsWithFileEvidence() {
        // §28.7-4: 첨부 파싱 텍스트 기반의 신호는 근거에 첨부 파일 참조를 남긴다
        UUID artifactId = UUID.randomUUID();
        session.setGoalText("블로그 사이트 만들어줘");
        session.setAttachments(java.util.List.of(new ClarificationSession.AttachmentRef(
                artifactId, "text/markdown", "보안요건.md", 20L, "PARSED")));
        com.autocompany.entity.Artifact artifact = com.autocompany.entity.Artifact.builder()
                .id(artifactId)
                .parsedText("개인정보 암호화 필수, 접근 인증 정책 적용")
                .build();
        when(artifactRepository.findAllById(java.util.List.of(artifactId))).thenReturn(java.util.List.of(artifact));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of());

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        RequirementSetService.RequirementItem security = outcome.content().nonFunctional().get(0);
        assertEquals("client_declared", security.source());
        assertTrue(security.evidence().contains("첨부 보안요건.md 파일")); // 항목 근거 = 첨부 참조(§5.25·§28.7-4)
        assertTrue(security.detail().contains("암호화"));
    }

    @Test
    void freezeSupportsAttachmentsOnlyGoal() {
        session.setGoalText(null); // §28.7(1): 첨부 단독 접수 세션
        UUID artifactId = UUID.randomUUID();
        session.setAttachments(java.util.List.of(new ClarificationSession.AttachmentRef(
                artifactId, "text/markdown", "보안요건.md", 20L, "PARSED")));
        com.autocompany.entity.Artifact artifact = com.autocompany.entity.Artifact.builder()
                .id(artifactId)
                .parsedText("개인정보 암호화 필수")
                .build();
        when(artifactRepository.findAllById(java.util.List.of(artifactId))).thenReturn(java.util.List.of(artifact));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of());

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertTrue(outcome.content().functional().stream()
                .noneMatch(i -> "초기 목표".equals(i.title()))); // 골 텍스트가 없으면 기록하지 않는다
        assertEquals(1, outcome.content().nonFunctional().size());
        assertTrue(outcome.content().nonFunctional().get(0).evidence().contains("첨부"));
    }

    // ==================== REQ-005 사용자 개설·명시 지시 동결 경로(§28.6) ====================

    @Test
    void freezeUserZeroQuestionPathRunsRecordApprovalOnly() {
        session.setStatus(ClarificationSession.SessionStatus.OPEN); // 개설 직후 — 질문 구성 없이 동결
        session.setOpenedByUserId(ownerId); // 사용자 개설 세션 표식
        session.setOpenedByEmployeeId(null);
        UUID approver1 = UUID.randomUUID();
        UUID approver2 = UUID.randomUUID();
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId)).thenReturn(List.of());
        when(reviewChainService.runRecordApproval(sessionId)).thenReturn(List.of(approver1, approver2));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        assertEquals(RequirementSet.SetStatus.FROZEN, outcome.set().getStatus());
        assertEquals(ClarificationSession.SessionStatus.FROZEN, session.getStatus());
        // §28.6 — 체인은 기록 승인만 수행, 승인자 목록이 frozen_by에 남는다
        assertEquals(List.of(approver1, approver2), outcome.set().getFrozenByEmployeeIds());
    }

    @Test
    void freezeWaitingClientWithoutDirectiveIsRejected() {
        session.setStatus(ClarificationSession.SessionStatus.WAITING_CLIENT);
        ClarificationQuestion unanswered = ClarificationQuestion.builder()
                .session(session).questionType(QuestionType.SHORT_TEXT)
                .prompt("기한").displayOrder(1).build();
        unanswered.setId(UUID.randomUUID());
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(unanswered));

        // 고객 명시 지시 없이 waiting_client 동결 불가
        assertThrows(IllegalStateException.class, () -> service.freeze(sessionId, ownerId));
    }

    @Test
    void freezeWaitingClientWithDirectiveMarksUnansweredAsSkippedWithAssumption() {
        session.setStatus(ClarificationSession.SessionStatus.WAITING_CLIENT);
        ClarificationQuestion answered = q(1, QuestionType.SINGLE_CHOICE, List.of("mvp"), null);
        ClarificationQuestion unanswered = ClarificationQuestion.builder()
                .session(session).questionType(QuestionType.SHORT_TEXT)
                .prompt("기한").displayOrder(2).build();
        unanswered.setId(UUID.randomUUID());
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(answered, unanswered));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId, true);

        // §28.6 "이 정도면 진행" — 미답 질문은 가정으로 기록된 뒤 동결
        assertEquals(ClarificationQuestion.AnswerStatus.SKIPPED, unanswered.getAnswerStatus());
        assertEquals("고객 명시 지시로 진행(이 정도면 진행, §28.6)", unanswered.getAssumptionIfSkipped());
        assertEquals(RequirementSet.SetStatus.FROZEN, outcome.set().getStatus());
        assertTrue(outcome.content().openRisks().stream()
                .anyMatch(i -> i.detail().contains("이 정도면 진행")));
    }

    // ==================== ORG-003 외부 고객 중계(§5.26) ====================

    @Test
    void freezeRelaySessionRecordsClientRelayedSourcesWithEvidence() {
        UUID clientId = UUID.randomUUID();
        session.setClientId(clientId);
        session.setGoalText("개인정보 보호가 필요한 상품 페이지 확장");
        Client client = Client.builder().organizationId(UUID.randomUUID()).displayName("대박커머스").build();
        client.setId(clientId);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(q(1, QuestionType.SINGLE_CHOICE, List.of("mvp"), null)));
        ArgumentCaptor<com.autocompany.entity.DiscussionMessage> msgCaptor =
                ArgumentCaptor.forClass(com.autocompany.entity.DiscussionMessage.class);

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        // 고객 원천 항목은 client_relayed + "외부 고객 {이름} 중계 — " 근거(§5.26)
        RequirementSetService.RequirementItem goal = outcome.content().functional().get(0);
        assertEquals("client_relayed", goal.source());
        assertTrue(goal.evidence().contains("대박커머스 중계"));
        RequirementSetService.RequirementItem scope = outcome.content().functional().get(1);
        assertEquals("client_relayed", scope.source());
        assertTrue(scope.evidence().contains("대박커머스 중계"));
        // 신호 항목도 중계 출처 유지
        assertTrue(outcome.content().nonFunctional().stream()
                .anyMatch(i -> "client_relayed".equals(i.source()) && i.evidence().contains("대박커머스 중계")));
        // 동결 감사 로그 — 중계 기록·원문 보존 명시
        verify(discussionMessageRepository).save(msgCaptor.capture());
        assertTrue(msgCaptor.getValue().getContent().contains("대박커머스 중계 기록(client_relayed)"));
    }

    @Test
    void freezeNonRelaySessionKeepsClientDeclaredSources() {
        when(questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId))
                .thenReturn(List.of(q(1, QuestionType.SINGLE_CHOICE, List.of("mvp"), null)));

        RequirementSetService.FrozenOutcome outcome = service.freeze(sessionId, ownerId);

        // client_id 없는 기존 세션은 기존 출처 표기 유지(회귀 방지)
        assertEquals("client_declared", outcome.content().functional().get(0).source());
        assertEquals("client_answered", outcome.content().functional().get(1).source());
    }
}
