package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.AnswerStatus;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.Client;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.RequirementSet;
import com.autocompany.entity.enums.MessageType;
import com.autocompany.repository.ClientRepository;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.RequirementSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 동결·RequirementSet 생성 서비스 (§5.25·§28.6, REQ-003).
 * 동결 시점: 고객 필수 답 완료(REVIEW) + 기록 승인. frozen 이후 수정 없음 — 진화는 새 세션(REQ-005).
 */
@Service
@RequiredArgsConstructor
public class RequirementSetService {

    private final RequirementSetRepository setRepository;
    private final ClarificationSessionRepository sessionRepository;
    private final ClarificationQuestionRepository questionRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final ClarificationExpertSignalDetector signalDetector;
    private final ObjectMapper objectMapper;
    private final com.autocompany.repository.ArtifactRepository artifactRepository;
    private final ClarificationReviewChainService reviewChainService;
    private final ClientRepository clientRepository;

    public record RequirementItem(String title, String detail, String source, String evidence, UUID questionId) {
    }

    public record RequirementContent(
            List<RequirementItem> functional,
            List<RequirementItem> nonFunctional,
            List<RequirementItem> constraints,
            List<RequirementItem> outOfScope,
            List<RequirementItem> openRisks) {
    }

    public record FrozenOutcome(RequirementSet set, RequirementContent content) {
    }

    @Transactional
    public FrozenOutcome freeze(UUID sessionId, UUID userId) {
        return freeze(sessionId, userId, null);
    }

    /**
     * 동결 (§28.6). 경로: ① REVIEW(필수 답 완료+체인 승인) ② clientDirective=true — waiting_client에서
     * "이 정도면 진행"(미답 질문은 가정으로 기록) ③ 사용자 개설 세션 질문 0개 경로(체인 기록 승인만 수행).
     */
    @Transactional
    public FrozenOutcome freeze(UUID sessionId, UUID userId, Boolean clientDirective) {
        ClarificationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("Clarification session not found: " + sessionId));
        if (userId == null || !userId.equals(session.getProject().getUser().getId())) {
            throw new IllegalArgumentException("Only the project owner (client position) can freeze");
        }
        if (session.getStatus() == SessionStatus.FROZEN) {
            throw new IllegalStateException("Session already frozen (§28.6) — declare a new session for changes (REQ-005)");
        }
        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled session cannot be frozen");
        }

        List<ClarificationQuestion> questions = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        boolean recordOnlyPath = questions.isEmpty()
                && session.getOpenedByUserId() != null
                && (session.getStatus() == SessionStatus.OPEN || session.getStatus() == SessionStatus.WAITING_CLIENT);
        boolean directivePath = Boolean.TRUE.equals(clientDirective)
                && session.getStatus() == SessionStatus.WAITING_CLIENT;

        if (directivePath) {
            // §28.6 "이 정도면 진행" — 미답 질문은 가정(assumption)으로 기록 후 동결
            for (ClarificationQuestion q : questions) {
                if (q.getAnswerStatus() == AnswerStatus.UNANSWERED) {
                    q.setAnswerStatus(AnswerStatus.SKIPPED);
                    q.setAssumptionIfSkipped("고객 명시 지시로 진행(이 정도면 진행, §28.6)");
                }
            }
            questionRepository.saveAll(questions);
        }
        if (!recordOnlyPath && !directivePath && session.getStatus() != SessionStatus.REVIEW) {
            throw new IllegalStateException("Freeze requires completed required answers (REVIEW state)"
                    + " — 사용자 개설 세션 질문 0개 경로·고객 명시 지시(§28.6) 외에는 불가");
        }

        boolean hasUnanswered = questions.stream().anyMatch(q -> q.getAnswerStatus() == AnswerStatus.UNANSWERED);
        if (hasUnanswered) {
            throw new IllegalStateException("Unanswered required questions remain");
        }

        RequirementContent content = buildContent(session, questions, resolveRelayClient(session));
        int version = nextVersion(session.getProject().getId());
        supersedePrevious(session.getProject().getId());

        List<UUID> frozenBy;
        if (recordOnlyPath) {
            frozenBy = reviewChainService.runRecordApproval(sessionId); // §28.6 체인은 기록 승인만 수행
        } else {
            frozenBy = questions.stream()
                    .map(q -> q.getReviewedByEmployeeIds() == null
                            ? java.util.List.<UUID>of()
                            : q.getReviewedByEmployeeIds())
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }

        RequirementSet set = RequirementSet.builder()
                .project(session.getProject())
                .session(session)
                .version(version)
                .contentJson(toJson(content))
                .status(RequirementSet.SetStatus.FROZEN)
                .frozenByEmployeeIds(frozenBy)
                .frozenAt(LocalDateTime.now())
                .build();
        set = setRepository.save(set);

        session.setStatus(SessionStatus.FROZEN);
        session.setFrozenAt(LocalDateTime.now());
        sessionRepository.save(session);
        UUID logSender = session.getOpenedByEmployeeId() != null
                ? session.getOpenedByEmployeeId()
                : (frozenBy.isEmpty() ? null : frozenBy.get(0));
        if (logSender != null) {
            discussionMessageRepository.save(DiscussionMessage.builder()
                    .project(session.getProject())
                    .senderEmployeeId(logSender)
                    .content("요구 사항 동결 — RequirementSet v" + version + " (§28.6 불변)"
                            + relayAuditSuffix(session))
                    .messageType(MessageType.DISCUSSION)
                    .build());
        }
        return new FrozenOutcome(set, content);
    }

    // ==================== ORG-003 중계 (§5.26) ====================

    /** 기존 연결은 아카이브 후에도 기록 보존을 위해 조회만 한다(신규 연결 검증은 서비스 진입점에서) */
    private Client resolveRelayClient(ClarificationSession session) {
        if (session.getClientId() == null) {
            return null;
        }
        return clientRepository.findById(session.getClientId()).orElse(null);
    }

    /** 동결 감사 로그 — 중계 세션은 원문 보존·client_relayed 기록을 남긴다(§5.26) */
    private String relayAuditSuffix(ClarificationSession session) {
        Client relayClient = resolveRelayClient(session);
        return relayClient == null ? ""
                : " — 외부 고객 " + relayClient.getDisplayName() + " 중계 기록(client_relayed), 원문 보존(§5.26)";
    }

    // ==================== 누적 기록 (§17) ====================

    @Transactional(readOnly = true)
    public List<RequirementSet> listByProject(UUID projectId) {
        return setRepository.findByProjectIdOrderByVersionDesc(projectId);
    }

    @Transactional(readOnly = true)
    public RequirementContent parseContent(RequirementSet set) {
        try {
            return objectMapper.readValue(set.getContentJson(), RequirementContent.class);
        } catch (Exception e) {
            throw new IllegalStateException("Requirement content parse failed: " + set.getId(), e);
        }
    }

    // ==================== 내부 ====================

    private RequirementContent buildContent(ClarificationSession session, List<ClarificationQuestion> questions,
                                            Client relayClient) {
        List<RequirementItem> functional = new ArrayList<>();
        List<RequirementItem> nonFunctional = new ArrayList<>();
        List<RequirementItem> constraints = new ArrayList<>();
        List<RequirementItem> outOfScope = new ArrayList<>();
        List<RequirementItem> openRisks = new ArrayList<>();

        // §5.25·§5.26: 운영자가 외부 고객 의견을 전달한 세션은 고객 원천 항목을 client_relayed로 기록
        String clientSource = relayClient == null ? "client_declared" : "client_relayed";
        String answerSource = relayClient == null ? "client_answered" : "client_relayed";
        String relayEvidence = relayClient == null ? "" : "외부 고객 " + relayClient.getDisplayName() + " 중계 — ";

        String goal = session.getGoalText();
        if (goal != null && !goal.isBlank()) {
            functional.add(new RequirementItem("초기 목표", truncate(goal), clientSource, relayEvidence + "goal_text 원문", null));
        }

        StringBuilder freeText = new StringBuilder();
        for (ClarificationQuestion q : questions) {
            if (q.getAnswerStatus() == AnswerStatus.SKIPPED || q.getAnswerStatus() == AnswerStatus.EXPIRED) {
                // §5.24 skipped는 assumption 기록에 한정해 동결 시점에 기록된다
                String assumption = q.getAssumptionIfSkipped() == null || q.getAssumptionIfSkipped().isBlank()
                        ? "세션 기록 기준"
                        : q.getAssumptionIfSkipped();
                openRisks.add(new RequirementItem("미응답 질문", truncate("건너뜀: " + q.getPrompt())
                        + " — assumption: " + assumption, "employee_derived", "question #" + q.getDisplayOrder(), q.getId()));
                continue;
            }
            String evidence = "question #" + q.getDisplayOrder();
            switch (q.getQuestionType()) {
                case SINGLE_CHOICE -> {
                    String value = firstValue(q);
                    String label = labelFor(q, value);
                    if (label != null) {
                        functional.add(new RequirementItem("확정 범위", "시작 수준 — " + label, answerSource, relayEvidence + evidence, q.getId()));
                        if ("mvp".equals(value)) {
                            outOfScope.add(new RequirementItem("1차 이후 확장", "해결하지 않은 편의·확장 항목은 차기 세션에서 반영", "employee_derived", evidence, q.getId()));
                        }
                    } else if (q.getOtherText() != null) {
                        // 기타(Other) 경로 — §28.3: 기타는 항상 제공
                        functional.add(new RequirementItem("확정 범위", "직접 응답 — " + truncate(q.getOtherText()), answerSource, relayEvidence + evidence, q.getId()));
                    }
                }
                case MULTI_CHOICE -> {
                    for (String value : q.getSelectedValues()) {
                        String label = labelFor(q, value);
                        if (label != null) {
                            functional.add(new RequirementItem("기능", label, answerSource, relayEvidence + evidence, q.getId()));
                        }
                    }
                }
                case SHORT_TEXT -> {
                    constraints.add(new RequirementItem("제약", itemText(q), answerSource, relayEvidence + evidence, q.getId()));
                    if (q.getOtherText() != null && !q.getOtherText().isBlank()) {
                        freeText.append("\n").append(q.getOtherText());
                    }
                }
                case FREE_TEXT -> {
                    constraints.add(new RequirementItem("고객 명시 요구", itemText(q), clientSource, relayEvidence + evidence, q.getId()));
                    if (q.getOtherText() != null && !q.getOtherText().isBlank()) {
                        freeText.append("\n").append(q.getOtherText());
                    }
                }
                case NUMBER_RANGE -> constraints.add(new RequirementItem("규모", "확정 예상 규모: " + q.getNumericAnswer(), answerSource, relayEvidence + evidence, q.getId()));
            }
        }

        // §28.2 signals → 명시 제약(client_declared; 중계 세션은 client_relayed) 기록. 암묵 재평가·무시 금지.
        // §28.7-4(REQ-004): 파싱된 첨부 텍스트도 출처별 판정 — 항목 근거에 첨부 파일 참조를 남긴다.
        recordSourceSignals(session, goal, "goal_text", functional, nonFunctional, constraints, clientSource, relayEvidence);
        recordSourceSignals(session, freeText.toString(), "답변 기타", functional, nonFunctional, constraints, clientSource, relayEvidence);
        if (session.getAttachments() != null && !session.getAttachments().isEmpty()) {
            java.util.Map<UUID, String> filenameByArtifact = new java.util.HashMap<>();
            for (ClarificationSession.AttachmentRef ref : session.getAttachments()) {
                filenameByArtifact.put(ref.artifactId(), ref.filename());
            }
            List<UUID> parsedIds = session.getAttachments().stream()
                    .filter(r -> "PARSED".equals(r.parseStatus()))
                    .map(ClarificationSession.AttachmentRef::artifactId)
                    .toList();
            for (com.autocompany.entity.Artifact artifact : artifactRepository.findAllById(parsedIds)) {
                recordSourceSignals(session, artifact.getParsedText(),
                        "첨부 " + filenameByArtifact.getOrDefault(artifact.getId(), artifact.getId().toString()) + " 파일",
                        functional, nonFunctional, constraints, clientSource, relayEvidence);
            }
        }
        return new RequirementContent(functional, nonFunctional, constraints, outOfScope, openRisks);
    }

    private void recordSourceSignals(ClarificationSession session, String text, String evidenceLabel,
                                     List<RequirementItem> functional, List<RequirementItem> nonFunctional,
                                     List<RequirementItem> constraints, String clientSource, String relayEvidence) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (ClarificationExpertSignalDetector.ExpertSignal s : signalDetector.detect(text)) {
            switch (s.domain()) {
                case TECH_STACK -> constraints.add(new RequirementItem("기술 스택 제약", "고객이 제시한 방식 수용: " + s.evidence(), clientSource, relayEvidence + evidenceLabel, null));
                case SECURITY -> nonFunctional.add(new RequirementItem("보안 요건", "고객 명시 신호: " + s.evidence(), clientSource, relayEvidence + evidenceLabel, null));
                case BUDGET -> constraints.add(new RequirementItem("비용 제약", "고객 발화 신호: " + s.evidence(), clientSource, relayEvidence + evidenceLabel, null));
                default -> { /* 디자인/UX/연동/기능 신호는 상위 답변 근거로 충족 — 중복 기록 생략 */ }
            }
        }
    }

    private String firstValue(ClarificationQuestion q) {
        return q.getSelectedValues() == null || q.getSelectedValues().isEmpty() ? null : q.getSelectedValues().get(0);
    }

    private String labelFor(ClarificationQuestion q, String value) {
        if (value == null) {
            return q.getOtherText();
        }
        String finalValue = value;
        Optional<ClarificationQuestion.QuestionOption> opt = q.getOptions().stream()
                .filter(o -> o.value() != null && o.value().equals(finalValue))
                .findFirst();
        return opt.map(ClarificationQuestion.QuestionOption::label).orElse(value);
    }

    private String itemText(ClarificationQuestion q) {
        String base = q.getOtherText() == null || q.getOtherText().isBlank()
                ? "응답 없음"
                : q.getOtherText();
        if (q.getSelectedValues() != null && !q.getSelectedValues().isEmpty()) {
            String labels = q.getSelectedValues().stream()
                    .map(v -> labelFor(q, v))
                    .collect(Collectors.joining(", "));
            base = labels + (base.isBlank() ? "" : " (" + base + ")");
        }
        return truncate(base);
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > 200 ? text.substring(0, 197) + "..." : text;
    }

    private int nextVersion(UUID projectId) {
        return setRepository.findByProjectIdOrderByVersionDesc(projectId).stream()
                .mapToInt(RequirementSet::getVersion)
                .max()
                .orElse(0) + 1;
    }

    private void supersedePrevious(UUID projectId) {
        List<RequirementSet> previous = setRepository.findByProjectIdOrderByVersionDesc(projectId);
        for (RequirementSet ps : previous) {
            if (ps.getStatus() == RequirementSet.SetStatus.FROZEN) {
                ps.setStatus(RequirementSet.SetStatus.SUPERSEDED);
                setRepository.save(ps);
            }
        }
    }

    private String toJson(RequirementContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("Requirement content serialize failed", e);
        }
    }
}
