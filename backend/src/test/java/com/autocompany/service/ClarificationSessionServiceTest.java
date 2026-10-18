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
import com.autocompany.entity.enums.Role;
import com.autocompany.repository.ArtifactRepository;
import com.autocompany.repository.ClarificationSessionRepository;
import com.autocompany.repository.DiscussionMessageRepository;
import com.autocompany.repository.EmployeeRepository;
import com.autocompany.repository.JobGradeRepository;
import com.autocompany.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
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
    private ArtifactRepository artifactRepository;
    private ClarificationAttachmentParser attachmentParser;
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
        artifactRepository = mock(ArtifactRepository.class);
        attachmentParser = new ClarificationAttachmentParser(); // 실제 파서 — 등급 게이트 포함
        service = new ClarificationSessionService(sessionRepository, projectRepository,
                employeeRepository, jobGradeRepository, discussionMessageRepository,
                artifactRepository, attachmentParser);

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
        when(artifactRepository.save(any(Artifact.class))).thenAnswer(inv -> {
            Artifact artifact = inv.getArgument(0);
            if (artifact.getId() == null) {
                artifact.setId(UUID.randomUUID());
            }
            savedArtifacts.add(artifact); // save 캡처 — findAllById는 mock이라 저장본을 돌려주지 않는다
            return artifact;
        });
    }

    private final java.util.List<Artifact> savedArtifacts = new java.util.ArrayList<>();

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

    // ==================== REQ-004 다중 모달 골 접수(§28.7) ====================

    private ClarificationSessionService.AttachmentInput md(String content) {
        return new ClarificationSessionService.AttachmentInput("요구사항.md", "text/markdown",
                (long) content.length(), Base64.getEncoder().encodeToString(content.getBytes()), null);
    }

    @Test
    void goalWithAttachmentsOnlyCreatesSessionAndParsedArtifact() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openPipelineSession(projectId, userId, null, List.of(md("회원가입, 로그인")));

        assertEquals(SessionStatus.OPEN, session.getStatus()); // 텍스트 없어도 첨부 단독 허용(§28.7-1)
        assertEquals(1, session.getAttachments().size());
        AttachmentRef ref = session.getAttachments().get(0);
        assertEquals("요구사항.md", ref.filename());
        assertEquals("PARSED", ref.parseStatus());

        Artifact artifact = savedArtifacts.get(0);
        assertEquals(ref.artifactId(), artifact.getId());
        assertEquals(Artifact.ParseStatus.PARSED, artifact.getParseStatus());
        assertEquals("회원가입, 로그인", artifact.getParsedText());
        assertEquals(64, artifact.getContentHash().length()); // SHA-256 근거(§28.7-4)
        assertTrue(artifact.getStorageUri().startsWith("artifact://"));
        assertEquals(Artifact.ArtifactType.DOCUMENT, artifact.getArtifactType());
    }

    @Test
    void goalWithNeitherTextNorAttachmentsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openPipelineSession(projectId, userId, "   ", List.of()));
    }

    @Test
    void tooManyAttachmentsAreRejected() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));
        List<ClarificationSessionService.AttachmentInput> eleven = java.util.stream.Stream
                .generate(() -> md("내용")).limit(11).toList(); // §23 max_count_per_goal 10 초과

        assertThrows(IllegalArgumentException.class,
                () -> service.openPipelineSession(projectId, userId, "골", eleven));
    }

    @Test
    void oversizedAttachmentIsRejected() {
        org.springframework.test.util.ReflectionTestUtils.setField(attachmentParser, "maxFileSizeMb", 1);
        byte[] big = new byte[2 * 1024 * 1024];
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        assertThrows(IllegalArgumentException.class, () -> service.openPipelineSession(projectId, userId, null,
                List.of(new ClarificationSessionService.AttachmentInput("큰파일.md", "text/markdown",
                        (long) big.length, Base64.getEncoder().encodeToString(big), null))));
    }

    @Test
    void contentUriOnlyAttachmentIsRejectedInMvp() {
        assertThrows(IllegalArgumentException.class, () -> service.openPipelineSession(projectId, userId, null,
                List.of(new ClarificationSessionService.AttachmentInput("외부파일.pdf", "application/pdf",
                        100L, null, "https://example.com/file.pdf"))));
    }

    @Test
    void invalidBase64IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.openPipelineSession(projectId, userId, null,
                List.of(new ClarificationSessionService.AttachmentInput("broken.md", "text/markdown",
                        10L, "!!not-base64!!", null))));
    }

    @Test
    void unsupportedAttachmentIsPreservedAndRequestsReinforce() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));
        ClarificationSessionService.AttachmentInput xlsx = new ClarificationSessionService.AttachmentInput(
                "요구정리.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                100L, Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), null);

        ClarificationSession session = service.openPipelineSession(projectId, userId, "블로그 만들어줘", List.of(xlsx));

        assertEquals("UNSUPPORTED", session.getAttachments().get(0).parseStatus()); // 원본 보존(§28.7-3)
        Artifact artifact = savedArtifacts.get(0);
        assertEquals(Artifact.ParseStatus.UNSUPPORTED, artifact.getParseStatus());
        assertNotNull(artifact.getStorageBlob());
        verify(discussionMessageRepository, times(2)).save(any(DiscussionMessage.class)); // 개설+보강 요청 기록
    }
}
