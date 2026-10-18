package com.autocompany.service;

import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.MessageType;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 요구사항 명확화 세션 서비스 (§28, REQ-001).
 * 골 접수 → 파이프라인 세션 개설(open) + 협의 개시 기록(토론 채널, §28.1).
 */
@Service
@RequiredArgsConstructor
public class ClarificationSessionService {

    private static final List<SessionStatus> ACTIVE_STATUSES =
            List.of(SessionStatus.OPEN, SessionStatus.WAITING_CLIENT);

    private final ClarificationSessionRepository sessionRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final DiscussionMessageRepository discussionMessageRepository;

    @Transactional
    public ClarificationSession openPipelineSession(UUID projectId, UUID userId, String goalText) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found: " + projectId));

        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        User owner = project.getUser();
        if (owner == null || !userId.equals(owner.getId())) {
            throw new IllegalArgumentException("Only the project owner can submit the goal");
        }
        if (goalText == null || goalText.isBlank()) {
            throw new IllegalArgumentException("goalText is required"); // §28.7 (1) — REQ-004에서 첨부 병행 허용
        }
        if (sessionRepository.existsByProjectIdAndStatusIn(projectId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("Active clarification session already exists for project (§28.6: 409)");
        }

        UUID openerId = findLeadPlannerId(project).orElse(null);
        if (openerId == null) {
            throw new IllegalStateException("PLANNER employee does not exist for the project owner");
        }

        ClarificationSession previous = sessionRepository
                .findFirstByProjectIdOrderBySessionNumberDesc(projectId)
                .orElse(null);
        int nextNumber = previous == null ? 1 : previous.getSessionNumber() + 1;

        ClarificationSession session = ClarificationSession.builder()
                .project(project)
                .goalText(goalText)
                .sessionNumber(nextNumber)
                .status(SessionStatus.OPEN)
                .openedByEmployeeId(openerId)
                .parentSessionId(previous == null ? null : previous.getId()) // §28.6 진화 승계
                .build();
        session = sessionRepository.save(session);

        // 협의 개시 기록 (토론 채널 재활용, §28.1)
        discussionMessageRepository.save(DiscussionMessage.builder()
                .project(project)
                .senderEmployeeId(openerId)
                .content("요구 명확화 세션 #" + nextNumber + " 개설 — 골 협의 개시")
                .messageType(MessageType.DISCUSSION)
                .build());
        return session;
    }

    @Transactional(readOnly = true)
    public List<ClarificationSession> getSessionsByProject(UUID projectId) {
        return sessionRepository.findByProjectIdOrderBySessionNumberDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<ClarificationSession> getSession(UUID sessionId) {
        return sessionRepository.findById(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ClarificationSession> getPendingSessionsForUser(UUID userId) {
        List<UUID> projectIds = projectRepository.findByUserId(userId).stream()
                .map(Project::getId)
                .toList();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        // §28.5 질문 수신함 — open|waiting_client 세션 대기 큐
        return sessionRepository.findByProjectIdInAndStatusInOrderBySessionNumberDesc(projectIds, ACTIVE_STATUSES);
    }

    private Optional<UUID> findLeadPlannerId(Project project) {
        return employeeRepository.findByUserIdAndRole(project.getUser().getId(), Role.PLANNER).stream()
                .max(Comparator.comparingInt(
                        (Employee e) -> jobGradeRepository.findById(e.getGradeId())
                                .map(JobGrade::getOrdinal)
                                .orElse(0)))
                .map(Employee::getId);
    }
}
