package com.autocompany.service;

import com.autocompany.entity.Artifact;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.AttachmentRef;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.Client;
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
import org.mockito.ArgumentCaptor;

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
    private ClientService clientService;
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
        clientService = mock(ClientService.class);
        service = new ClarificationSessionService(sessionRepository, projectRepository,
                employeeRepository, jobGradeRepository, discussionMessageRepository,
                artifactRepository, attachmentParser, clientService);

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

    // ==================== REQ-005 사용자 개설 세션(§28.6) ====================

    private ClarificationSession previousSession(int number) {
        ClarificationSession previous = ClarificationSession.builder()
                .project(project)
                .goalText("1차 골")
                .sessionNumber(number)
                .status(SessionStatus.FROZEN)
                .openedByEmployeeId(planner.getId())
                .build();
        when(sessionRepository.findFirstByProjectIdOrderBySessionNumberDesc(projectId))
                .thenReturn(Optional.of(previous));
        return previous;
    }

    @Test
    void userSessionCreatesOpenSessionPreservingChangeNoteAndParent() {
        ClarificationSession previous = previousSession(3);
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openUserSession(projectId, userId, "요금제 문구 수정 요청", List.of());

        assertEquals(4, session.getSessionNumber()); // max+1 승계
        assertEquals(previous.getId(), session.getParentSessionId()); // 진화 연결
        assertEquals(SessionStatus.OPEN, session.getStatus());
        assertEquals(userId, session.getOpenedByUserId()); // 파이프라인 개설과 구분
        assertNull(session.getOpenedByEmployeeId());
        assertEquals("요금제 문구 수정 요청", session.getGoalText()); // 개설 취지 원문 보존
        verify(discussionMessageRepository).save(any(DiscussionMessage.class)); // 대표 PLANNER 접수 기록
    }

    @Test
    void userSessionWithAttachmentsOnlyIsAllowed() {
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openUserSession(projectId, userId, null, List.of(md("화면 문구 시안")));

        assertEquals(SessionStatus.OPEN, session.getStatus()); // changeNote 없어도 첨부 단독 허용(§28.7-1)
        assertNull(session.getGoalText());
        assertEquals(1, session.getAttachments().size());
        assertEquals("PARSED", session.getAttachments().get(0).parseStatus());
    }

    @Test
    void userSessionWithoutNoteAndAttachmentsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openUserSession(projectId, userId, "  ", List.of()));
    }

    @Test
    void userSessionWhileActiveSessionExistsIsRejected() {
        when(sessionRepository.existsByProjectIdAndStatusIn(projectId,
                List.of(SessionStatus.OPEN, SessionStatus.WAITING_CLIENT))).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.openUserSession(projectId, userId, "수정 요청", List.of()));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void userSessionByNonOwnerIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.openUserSession(projectId, UUID.randomUUID(), "수정 요청", List.of()));
    }

    @Test
    void userSessionWithoutPlannerEmployeeStillCreatesSession() {
        // 직원 부재 조직 — 접수 기록만 생략, 세션 개설은 유효(사용자 개설 경로)
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of());

        ClarificationSession session = service.openUserSession(projectId, userId, "수정 요청", List.of());

        assertEquals(SessionStatus.OPEN, session.getStatus());
        verify(discussionMessageRepository, never()).save(any());
    }

    // ==================== REQ-005 세션 취소(§28.6) ====================

    private ClarificationSession cancelFixture(SessionStatus status) {
        ClarificationSession session = ClarificationSession.builder()
                .project(project)
                .goalText("블로그 사이트")
                .sessionNumber(1)
                .status(status)
                .openedByEmployeeId(planner.getId())
                .build();
        session.setId(UUID.randomUUID());
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void cancelOpenSessionMarksCancelledWithClosedAtAndAuditLog() {
        ClarificationSession session = cancelFixture(SessionStatus.OPEN);

        ClarificationSession result = service.cancelSession(session.getId(), userId);

        assertEquals(SessionStatus.CANCELLED, result.getStatus());
        assertNotNull(result.getClosedAt());
        verify(discussionMessageRepository).save(any(DiscussionMessage.class)); // Audit 기록
    }

    @Test
    void cancelWaitingClientSessionIsAllowed() {
        ClarificationSession session = cancelFixture(SessionStatus.WAITING_CLIENT);

        ClarificationSession result = service.cancelSession(session.getId(), userId);

        assertEquals(SessionStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelFrozenSessionIsRejected() {
        ClarificationSession session = cancelFixture(SessionStatus.FROZEN);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.cancelSession(session.getId(), userId));
        assertTrue(e.getMessage().contains("Frozen"));
    }

    @Test
    void cancelReviewSessionIsRejected() {
        ClarificationSession session = cancelFixture(SessionStatus.REVIEW);

        assertThrows(IllegalStateException.class, () -> service.cancelSession(session.getId(), userId));
    }

    @Test
    void cancelByNonOwnerIsRejected() {
        ClarificationSession session = cancelFixture(SessionStatus.OPEN);

        assertThrows(IllegalArgumentException.class,
                () -> service.cancelSession(session.getId(), UUID.randomUUID()));
    }

    @Test
    void cancelMissingSessionIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(sessionRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.cancelSession(missing, userId));
    }

    // ==================== ORG-003 외부 고객 중계(§5.26) ====================

    private Client activeClient(UUID clientId, String name) {
        Client client = Client.builder().organizationId(UUID.randomUUID()).displayName(name).build();
        client.setId(clientId);
        return client;
    }

    @Test
    void goalSessionInheritsProjectClientIdForRelay() {
        UUID clientId = UUID.randomUUID();
        project.setClientId(clientId);
        when(clientService.requireActiveForRelay(clientId)).thenReturn(activeClient(clientId, "대박커머스"));
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));
        ArgumentCaptor<DiscussionMessage> captor = ArgumentCaptor.forClass(DiscussionMessage.class);

        ClarificationSession session = service.openPipelineSession(projectId, userId, "상품 페이지 추가 요청");

        assertEquals(clientId, session.getClientId()); // Project.client_id 상속(§5.26)
        verify(discussionMessageRepository).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("대박커머스 중계")); // 중계 감사 문구
        assertTrue(captor.getValue().getContent().contains("원문 보존"));
    }

    @Test
    void goalSessionClientIdOverrideTakesPrecedenceOverProject() {
        UUID overrideId = UUID.randomUUID(); // Project.client_id는 null
        when(clientService.requireActiveForRelay(overrideId)).thenReturn(activeClient(overrideId, "미니샵"));
        when(employeeRepository.findByUserIdAndRole(userId, Role.PLANNER)).thenReturn(List.of(planner));
        when(jobGradeRepository.findById(leadGrade.getId())).thenReturn(Optional.of(leadGrade));

        ClarificationSession session = service.openPipelineSession(
                projectId, userId, "골", List.of(), overrideId);

        assertEquals(overrideId, session.getClientId()); // 세션 생성 API override
    }

    @Test
    void userSessionInheritsProjectClientId() {
        UUID clientId = UUID.randomUUID();
        project.setClientId(clientId);
        when(clientService.requireActiveForRelay(clientId)).thenReturn(activeClient(clientId, "고객A"));

        ClarificationSession session = service.openUserSession(projectId, userId, "요금제 수정", List.of());

        assertEquals(clientId, session.getClientId());
    }

    @Test
    void archivedClientRelayIsRejected() {
        UUID clientId = UUID.randomUUID();
        when(clientService.requireActiveForRelay(clientId)).thenThrow(
                new IllegalStateException("Archived client cannot be used for new relays (§5.26: 409)"));

        assertThrows(IllegalStateException.class,
                () -> service.openUserSession(projectId, userId, "수정", List.of(), clientId));
        verify(sessionRepository, never()).save(any());
    }
}
