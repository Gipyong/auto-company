package com.autocompany.service;

import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.enums.MessageType;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ClarificationQuestionRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 검토 체인 (§28.4, REQ-003).
 * 질문은 체인 통과 후에만 waiting_client로 전달된다. 역할 기반 순차 체인:
 * [완성] PLANNER 작성 → ARCHITECT(범위·기술 과노출) → QA(인수조건 전환·모호성)
 *      → SECURITY(관련 시에만) → PM/기술문서(이해 용이성) → waiting_client 전송
 * 통과 못한 질문은 다시 협의(OPEN 유지). 없는 역할은 건너뛰고 감사 기록. 시의 LLM 접점은 Mock 규칙.
 */
@Service
@RequiredArgsConstructor
public class ClarificationReviewChainService {

    // §28.4 체인 순서 — §23 review_chain_roles와 동일 구성의 구현 상수
    private static final List<Role> CHAIN_ROLES = List.of(
            Role.PLANNER, Role.ARCHITECT, Role.QA_ENGINEER, Role.SECURITY_DEVOPS, Role.PROJECT_MANAGER);

    // §28.2 미포함 항목(기술 스택·아키텍처 노출)을 질문에 섞어 쓰는지 아키텍처 검역에서 차단하는 규칙(Mock)
    private static final Pattern TECH_PROMPT_PATTERN = Pattern.compile(
            "(?i)(spring|react|postgres|mysql|docker|kubernetes|js|java|python|api 패턴|마이크로서비스|server|client\\b)");

    private final ClarificationQuestionRepository questionRepository;
    private final ClarificationSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final ClarificationExpertSignalDetector signalDetector;

    @Transactional
    public boolean runReviewChain(UUID sessionId) {
        ClarificationSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Clarification session not found: " + sessionId));
        List<ClarificationQuestion> questions = questionRepository.findBySessionIdOrderByDisplayOrderAsc(sessionId);
        UUID ownerId = session.getProject().getUser().getId();
        boolean securityRelated = isSecurityRelated(session);

        for (ClarificationQuestion question : questions) {
            for (Role role : CHAIN_ROLES) {
                if (role == Role.SECURITY_DEVOPS && !securityRelated) {
                    continue; // §28.4 [4] "해당 시에만"
                }
                String reviewerId = reviewerIdFor(ownerId, role);
                if (reviewerId == null) {
                    log(session, "체인 감사: [" + role + "] 역할 부재로 건너뜀(질문 #" + question.getDisplayOrder() + ")");
                    continue;
                }
                if (isRejected(role, question)) {
                    log(session, "체인 반송: [" + role + "] 검토 보류 — 질문 #" + question.getDisplayOrder()
                            + " 재작성 필요(협의 회귀, §13)");
                    return false;
                }
                markReviewed(question, UUID.fromString(reviewerId));
            }
        }
        questionRepository.saveAll(questions);

        if (session.getStatus() == SessionStatus.OPEN) {
            session.setStatus(SessionStatus.WAITING_CLIENT);
            sessionRepository.save(session);
            log(session, "체인 통과 — 고객 질문 전송(§28.5 waiting_client 큐)");
        }
        return true;
    }

    private void log(ClarificationSession session, String content) {
        discussionMessageRepository.save(DiscussionMessage.builder()
                .project(session.getProject())
                .senderEmployeeId(session.getOpenedByEmployeeId())
                .content(content)
                .messageType(MessageType.DISCUSSION)
                .build());
    }

    private void markReviewed(ClarificationQuestion q, UUID reviewerId) {
        if (q.getReviewedByEmployeeIds() == null) {
            q.setReviewedByEmployeeIds(new java.util.ArrayList<>());
        }
        if (!q.getReviewedByEmployeeIds().contains(reviewerId)) {
            q.getReviewedByEmployeeIds().add(reviewerId); // §5.24 체인 이력 기록
        }
    }

    private boolean isRejected(Role role, ClarificationQuestion question) {
        if (role == Role.ARCHITECT) {
            return TECH_PROMPT_PATTERN.matcher(question.getPrompt() == null ? "" : question.getPrompt()).find();
        }
        return false; // Mock 규칙 — LLM 접점(MODEL-002 이후) 아님
    }

    private boolean isSecurityRelated(ClarificationSession session) {
        String probe = session.getGoalText() == null ? "" : session.getGoalText();
        if (session.getClientExtraNote() != null) {
            probe += "\n" + session.getClientExtraNote();
        }
        return signalDetector.detect(probe).stream()
                .anyMatch(s -> s.domain() == ClarificationExpertSignalDetector.Domain.SECURITY);
    }

    private String reviewerIdFor(UUID userId, Role role) {
        return employeeRepository.findByUserIdAndRole(userId, role).stream()
                .max(java.util.Comparator.comparingInt(
                        (Employee e) -> jobGradeRepository.findById(e.getGradeId())
                                .map(JobGrade::getOrdinal)
                                .orElse(0)))
                .map(e -> e.getId().toString())
                .orElse(null);
    }
}
