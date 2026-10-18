package com.autocompany.service;

import com.autocompany.entity.Artifact;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.AttachmentRef;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.DiscussionMessage;
import com.autocompany.entity.Employee;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.entity.enums.MessageType;
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ArtifactRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 요구사항 명확화 세션 서비스 (§28, REQ-001).
 * 골 접수(텍스트+첨부 §28.7, REQ-004) → Artifact 저장·파싱 → 파이프라인 세션 개설(open)
 * + 협의 개시 기록(토론 채널, §28.1). 첨부 원본은 정적 저장만(실행 금지, §28.7-5).
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
    private final ArtifactRepository artifactRepository;
    private final ClarificationAttachmentParser attachmentParser;

    /** §28.7(1) 첨부 입력 — MVP는 contentBase64만 지원(§28.7-6) */
    public record AttachmentInput(String filename, String mediaType, Long sizeBytes,
                                  String contentBase64, String contentUri) {
    }

    @Transactional
    public ClarificationSession openPipelineSession(UUID projectId, UUID userId, String goalText) {
        return openPipelineSession(projectId, userId, goalText, List.of());
    }

    @Transactional
    public ClarificationSession openPipelineSession(UUID projectId, UUID userId,
                                                    String goalText, List<AttachmentInput> attachments) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Project not found: " + projectId));

        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        User owner = project.getUser();
        if (owner == null || !userId.equals(owner.getId())) {
            throw new IllegalArgumentException("Only the project owner can submit the goal");
        }
        // §28.7(1): 텍스트·첨부 둘 다 비면 400
        if ((goalText == null || goalText.isBlank()) && (attachments == null || attachments.isEmpty())) {
            throw new IllegalArgumentException("goalText or attachments is required (§28.7: min 1 of 2, 400)");
        }
        if (sessionRepository.existsByProjectIdAndStatusIn(projectId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("Active clarification session already exists for project (§28.6: 409)");
        }

        List<Artifact> saved = List.of();
        List<AttachmentRef> refs = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            saved = storeAttachments(project, attachments, refs);
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
                .goalText(goalText) // 텍스트 없는 첨부 접수 허용(§28.7-6)
                .sessionNumber(nextNumber)
                .status(SessionStatus.OPEN)
                .openedByEmployeeId(openerId)
                .parentSessionId(previous == null ? null : previous.getId()) // §28.6 진화 승계
                .attachments(refs)
                .build();
        session = sessionRepository.save(session);

        // 협의 개시 기록 (토론 채널 재활용, §28.1)
        discussionMessageRepository.save(DiscussionMessage.builder()
                .project(project)
                .senderEmployeeId(openerId)
                .content("요구 명확화 세션 #" + nextNumber + " 개설 — 골 협의 개시"
                        + (saved.isEmpty() ? "" : " (첨부 " + saved.size() + "건 접수, Artifact 보존 §28.7)"))
                .messageType(MessageType.DISCUSSION)
                .build());
        logParseSummary(project, openerId, saved);
        return session;
    }

    // ==================== REQ-004 첨부 저장·파싱 (§28.7) ====================

    private List<Artifact> storeAttachments(Project project, List<AttachmentInput> attachments,
                                            List<AttachmentRef> refsOut) {
        if (attachments.size() > attachmentParser.maxCountPerGoal()) {
            throw new IllegalArgumentException("Too many attachments: "
                    + attachments.size() + " > max_count_per_goal="
                    + attachmentParser.maxCountPerGoal() + " (§28.7-5: 400)");
        }

        List<Artifact> saved = new ArrayList<>();
        for (AttachmentInput input : attachments) {
            if (input == null || input.filename() == null || input.filename().isBlank()) {
                throw new IllegalArgumentException("attachment filename is required");
            }
            if (input.contentBase64() == null || input.contentBase64().isBlank()) {
                throw new IllegalArgumentException("attachment contentBase64 is required (MVP §28.7-6)"
                        + (input.contentUri() == null || input.contentUri().isBlank()
                        ? "" : " — contentUri is not supported in MVP"));
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(input.contentBase64());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("attachment contentBase64 is not valid base64: "
                        + input.filename());
            }
            if (bytes.length > attachmentParser.maxFileSizeBytes()) {
                throw new IllegalArgumentException("Attachment too large: " + input.filename()
                        + " (" + bytes.length + " bytes > max_file_size "
                        + attachmentParser.maxFileSizeBytes() + ", §28.7-5: 400)");
            }

            String mediaType = input.mediaType() == null || input.mediaType().isBlank()
                    ? "application/octet-stream" : input.mediaType();
            // native 형식은 UTF-8 텍스트 자체 파싱, 나머지 등급은 파서가 상태만 판정(§28.7-3)
            ClarificationAttachmentParser.ParseResult result =
                    attachmentParser.parse(new String(bytes, StandardCharsets.UTF_8), input.filename(), mediaType);

            Artifact artifact = Artifact.builder()
                    .project(project)
                    .artifactType(ClarificationAttachmentParser.Tier.IMAGE.equals(result.tier())
                            ? Artifact.ArtifactType.IMAGE : Artifact.ArtifactType.DOCUMENT)
                    .storageUri("artifact://pending") // 저장 후 id 확정
                    .contentHash(sha256Hex(bytes))
                    .sizeBytes(bytes.length)
                    .mediaType(mediaType)
                    .parseStatus(result.status())
                    .parseEvidence(List.of(result.note()))
                    .parsedText(result.parsedText())
                    .storageBlob(bytes) // 정적 저장만 — 실행 대상 아님(§28.7-5)
                    .build();
            artifact = artifactRepository.save(artifact);
            artifact.setStorageUri("artifact://" + artifact.getId());
            artifact = artifactRepository.save(artifact);

            refsOut.add(new AttachmentRef(artifact.getId(), mediaType, input.filename(),
                    bytes.length, artifact.getParseStatus().name()));
            saved.add(artifact);
        }
        return saved;
    }

    private void logParseSummary(Project project, UUID openerId, List<Artifact> saved) {
        if (saved.isEmpty()) {
            return;
        }
        long parsed = saved.stream().filter(a -> a.getParseStatus() == Artifact.ParseStatus.PARSED).count();
        long unsupported = saved.stream().filter(a -> a.getParseStatus() == Artifact.ParseStatus.UNSUPPORTED).count();
        long pendingVision = saved.stream().filter(a -> a.getParseStatus() == Artifact.ParseStatus.IMAGE_PENDING_VISION).count();
        StringBuilder line = new StringBuilder("[첨부 파싱 §28.7-3] parsed " + parsed + ", unsupported " + unsupported
                + ", image_pending_vision " + pendingVision);
        if (unsupported > 0) {
            line.append(" — 파싱 불가 형식은 파일 원본을 보존했으니 내용을 텍스트로 보강 요청(첨부+텍스트 병행 안내)");
        } else if (pendingVision > 0) {
            line.append(" — 이미지는 vision 모델 배정 후 해석 예정");
        }
        discussionMessageRepository.save(DiscussionMessage.builder()
                .project(project)
                .senderEmployeeId(openerId)
                .content(line.toString())
                .messageType(MessageType.DISCUSSION)
                .build());
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

    /** §28.7-5: 신뢰 불가 데이터 — 내용 자체는 파싱 대상일 뿐, 지시로 취급하지 않는다(§8.2) */
    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}
