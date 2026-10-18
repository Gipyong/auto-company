package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.AnswerStatus;
import com.autocompany.entity.ClarificationQuestion.QuestionOption;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.enums.MessageType;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 질문 구성기 + 구조화 답변 수집 (§28.3·§28.2, REQ-002).
 * 세션당 max_questions 상한, 선택형 개별 옵션, 기타(Other) 항상 제공(§28.3).
 */
@Service
@RequiredArgsConstructor
public class ClarificationQuestionService {

    private final ClarificationQuestionRepository questionRepository;
    private final ClarificationSessionRepository sessionRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final ClarificationReviewChainService reviewChainService;
    private final ClarificationExpertSignalDetector signalDetector;

    // ==================== 질문 구성 ====================

    @Transactional
    public List<ClarificationQuestion> composeQuestions(UUID sessionId) {
        ClarificationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Clarification session not found: " + sessionId));
        if (questionRepository.existsBySessionId(sessionId)) {
            throw new IllegalStateException("Questions already composed for this session (regeneration not allowed)");
        }
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Questions can only be composed for an OPEN session");
        }
        UUID authorId = session.getOpenedByEmployeeId();
        if (authorId == null) {
            throw new IllegalStateException("Questions must be composed by the pipeline opener employee");
        }

        List<ClarificationExpertSignalDetector.Domain> exemptedDomains = recordSignals(session, session.getGoalText());

        boolean featureExempt = exemptedDomains.contains(ClarificationExpertSignalDetector.Domain.FEATURES);
        List<ClarificationQuestion> composed = new ArrayList<>();
        int order = 1;
        for (ClarificationQuestion template : templates(session)) {
            if (template.getQuestionType() == QuestionType.MULTI_CHOICE && featureExempt) {
                continue; // §28.2.2 이미 기능을 제시한 영역은 질문 면제
            }
            if (order > session.getMaxQuestions()) {
                break; // §28.3 max_questions 상한
            }
            ClarificationQuestion question = ClarificationQuestion.builder()
                    .session(session)
                    .displayOrder(order)
                    .questionType(template.getQuestionType())
                    .prompt(template.getPrompt())
                    .options(template.getOptions())
                    .allowOther(template.isAllowOther())
                    .createdByEmployeeId(authorId)
                    .build();
            composed.add(questionRepository.save(question));
            order++;
        }

        // §28.4: 질문은 체인 통과 후에만 waiting_client 전송 — 통과 못하면 OPEN 유지(재협의)
        boolean passed = reviewChainService.runReviewChain(sessionId);
        if (passed) {
            session.setStatus(SessionStatus.WAITING_CLIENT);
            session = sessionRepository.save(session);
        }
        discussionMessageRepository.save(DiscussionMessage.builder()
                .project(session.getProject())
                .senderEmployeeId(authorId)
                .content("질문 " + composed.size() + "개 구성 — 검토 체인 "
                        + (passed ? "통과 → 고객 응답 대기 큐(§28.5)" : "보류 → 재검토(OPEN 유지)"))
                .messageType(MessageType.DISCUSSION)
                .build());
        return composed;
    }

    // [0단계] 신호 기록 + 면제 도메인 목록(§28.4) — 협의 기록은 토론 채널로
    private List<ClarificationExpertSignalDetector.Domain> recordSignals(ClarificationSession session, String text) {
        List<ClarificationExpertSignalDetector.Domain> exempted = new ArrayList<>();
        UUID authorId = session.getOpenedByEmployeeId();
        for (ClarificationExpertSignalDetector.ExpertSignal signal : signalDetector.detect(text)) {
            exempted.add(signal.domain());
            discussionMessageRepository.save(DiscussionMessage.builder()
                    .project(session.getProject())
                    .senderEmployeeId(authorId)
                    .content("[0단계 신호 선식별] [" + signal.domain() + "] 감지(" + signal.evidence()
                            + ") — 해당 영역 질문 면제·간략화, 근거 원문(goal_text) 기록")
                    .messageType(MessageType.DISCUSSION)
                    .build());
        }
        return exempted;
    }

    // §28.3 예시 포맷 — 산업 용어만, 기술 질문 미포함(§28.2)
    private List<ClarificationQuestion> templates(ClarificationSession session) {
        List<ClarificationQuestion> templates = new ArrayList<>();
        templates.add(singleChoice("이번 결과물을 어느 수준에서 먼저 확정하고 싶으신가요?", List.of(
                new QuestionOption("mvp", "먼저 간단한 MVP로 시작 — 핵심 기능 위주", null),
                new QuestionOption("full", "실제 서비스 수준의 완성품으로 한 번에", null))));
        templates.add(multiChoice("원하는 기능을 모두 선택해 주세요.", List.of(
                new QuestionOption("member", "회원가입/로그인", null),
                new QuestionOption("content", "콘텐츠 작성·편집", null),
                new QuestionOption("reaction", "댓글·의견 교류", null),
                new QuestionOption("search", "검색", null),
                new QuestionOption("upload", "파일·사진 업로드", null),
                new QuestionOption("integration", "외부 서비스 연동", null))));
        templates.add(singleChoice("먼저 우선해야 하는 쪽은?", List.of(
                new QuestionOption("quality", "품질 우선 — 검토·완성도", null),
                new QuestionOption("speed", "속도 우선 — 빠른 결과 유지", null),
                new QuestionOption("cost", "비용 우선 — 지출 최소화", null))));
        templates.add(ClarificationQuestion.builder()
                .questionType(QuestionType.SHORT_TEXT)
                .prompt("기한이나 반드시 지켜야 할 규칙(예산·금지 사항)을 적어주세요. 없으면 '확인' 버튼만 눌러 건너뛰셔도 됩니다.")
                .allowOther(true)
                .build());
        templates.add(ClarificationQuestion.builder()
                .questionType(QuestionType.FREE_TEXT)
                .prompt("전문적인 요구사항이 있으면 추가로 작성해주세요. 없으면 비워도 됩니다. (예: 특정 규칙, 보안·개인정보 조건, 성능 요건)")
                .allowOther(false)
                .build());
        templates.add(ClarificationQuestion.builder()
                .questionType(QuestionType.NUMBER_RANGE)
                .prompt("이미 확정된 예상 인원(사용자 수)이 있다면 숫자로 적어주세요.")
                .allowOther(true)
                .build());
        return templates;
    }

    private ClarificationQuestion singleChoice(String prompt, List<QuestionOption> options) {
        return ClarificationQuestion.builder().questionType(QuestionType.SINGLE_CHOICE)
                .prompt(prompt).options(options).allowOther(true).build();
    }

    private ClarificationQuestion multiChoice(String prompt, List<QuestionOption> options) {
        return ClarificationQuestion.builder().questionType(QuestionType.MULTI_CHOICE)
                .prompt(prompt).options(options).allowOther(true).build();
    }

    // ==================== 답변 수집 ====================

    @Transactional
    public ClarificationSession submitAnswers(UUID sessionId, UUID userId,
                                                     List<AnswerItem> items) {
        ClarificationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Clarification session not found: " + sessionId));
        if (userId == null || !userId.equals(session.getProject().getUser().getId())) {
            throw new IllegalArgumentException("Only the project owner (client position) can answer");
        }
        if (session.getStatus() == SessionStatus.FROZEN || session.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException("Session is already " + session.getStatus() + " (§28.6: 409)");
        }
        List<ClarificationQuestion> questions = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        if (questions.isEmpty()) {
            throw new IllegalStateException("Questions are not prepared yet for this session");
        }

        for (AnswerItem item : items) {
            ClarificationQuestion q = questionRepository.findById(item.questionId())
                    .orElseThrow(() -> new NoSuchElementException("Question not found: " + item.questionId()));
            if (!q.getSession().getId().equals(sessionId)) {
                throw new IllegalArgumentException("Question does not belong to this session: " + item.questionId());
            }
            applyAnswer(q, item);
            questionRepository.save(q);
        }

        List<ClarificationQuestion> uninitializedQuestions = questions.stream()
                .filter(q -> q.getAnswerStatus() == AnswerStatus.UNANSWERED)
                .toList();
        if (!uninitializedQuestions.isEmpty()) {
            // §17: 필수 미답 시 409 + 남은 질문 정보
            throw new RemainingAnswersException(uninitializedQuestions.stream()
                    .map(RemainingAnswerView::from)
                    .toList());
        }
        if (session.getStatus() == SessionStatus.WAITING_CLIENT) {
            session.setStatus(SessionStatus.REVIEW); // 답변 접수 → 검토(§28.5 review)
            sessionRepository.save(session);
            discussionMessageRepository.save(DiscussionMessage.builder()
                    .project(session.getProject())
                    .senderEmployeeId(session.getOpenedByEmployeeId())
                    .content("고객 답변 접수 — 요구 해석·검토 진입(§28.5 review)")
                    .messageType(MessageType.DISCUSSION)
                    .build());
        }
        return session;
    }

    private void applyAnswer(ClarificationQuestion q, AnswerItem item) {
        if (item.skipped() != null && item.skipped()) {
            q.setAnswerStatus(AnswerStatus.SKIPPED); // assumption은 REQ-003 체인에서 기록
            return;
        }
        List<String> values = item.selectedValues() == null ? List.of() : item.selectedValues();
        boolean hasOther = item.otherText() != null && !item.otherText().isBlank();
        switch (q.getQuestionType()) {
            case SINGLE_CHOICE -> {
                if (values.size() > 1) {
                    throw new IllegalArgumentException("single_choice accepts exactly one option: " + q.getId());
                }
                if (values.size() == 1) {
                    requireOptionValue(q, values.get(0));
                    q.setSelectedValues(values);
                    q.setOtherText(item.otherText());
                } else {
                    if (!q.isAllowOther() || !hasOther) {
                        throw new IllegalArgumentException("single_choice requires one option: " + q.getId());
                    }
                    q.setSelectedValues(null);
                    q.setOtherText(item.otherText());
                }
            }
            case MULTI_CHOICE -> {
                for (String value : values) {
                    requireOptionValue(q, value);
                }
                if (!values.isEmpty()) {
                    q.setSelectedValues(values);
                    q.setOtherText(item.otherText());
                } else {
                    if (!q.isAllowOther() || !hasOther) {
                        throw new IllegalArgumentException("multi_choice requires at least one option: " + q.getId());
                    }
                    q.setSelectedValues(null);
                    q.setOtherText(item.otherText());
                }
            }
            case SHORT_TEXT, FREE_TEXT -> {
                if (!hasOther) {
                    throw new IllegalArgumentException("text question requires an answer: " + q.getId());
                }
                q.setOtherText(item.otherText());
            }
            case NUMBER_RANGE -> {
                if (item.numericAnswer() == null) {
                    throw new IllegalArgumentException("number_range requires a numeric answer: " + q.getId());
                }
                q.setNumericAnswer(item.numericAnswer());
            }
        }
        q.setAnswerStatus(AnswerStatus.ANSWERED);
    }

    private void requireOptionValue(ClarificationQuestion q, String value) {
        boolean exists = q.getOptions() != null && q.getOptions().stream()
                .anyMatch(o -> o.value() != null && o.value().equals(value));
        if (!exists) {
            throw new IllegalArgumentException("Unknown option value: " + value);
        }
    }

    // ==================== 조회 (§28.4: 체인 통과 후에만 표시) ====================

    @Transactional(readOnly = true)
    public List<ClarificationQuestion> getQuestions(UUID sessionId) {
        ClarificationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Clarification session not found: " + sessionId));
        if (session.getStatus() == SessionStatus.OPEN) {
            throw new IllegalStateException("Questions are not released yet — pending review chain (§28.4)");
        }
        return questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
    }

    // ==================== DTO ====================

    public record AnswerItem(UUID questionId, List<String> selectedValues,
                             String otherText, java.math.BigDecimal numericAnswer, Boolean skipped) {
    }

    public record SubmitAnswersRequest(UUID userId, List<AnswerItem> answers) {
    }

    public record RemainingAnswerView(UUID questionId, int displayOrder, String prompt,
                                      String questionType, String answerStatus) {
        public static RemainingAnswerView from(ClarificationQuestion q) {
            return new RemainingAnswerView(q.getId(), q.getDisplayOrder(), q.getPrompt(),
                    q.getQuestionType().name(), q.getAnswerStatus().name());
        }
    }

    public static class RemainingAnswersException extends RuntimeException {
        public final List<RemainingAnswerView> remaining;

        public RemainingAnswersException(List<RemainingAnswerView> remaining) {
            super("Required answers missing (" + remaining.size() + " remaining)");
            this.remaining = remaining;
        }
    }
}
