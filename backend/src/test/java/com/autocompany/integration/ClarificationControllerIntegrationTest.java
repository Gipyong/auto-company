package com.autocompany.integration;

import com.autocompany.controller.ClarificationController;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.Project;
import com.autocompany.service.ClarificationQuestionService;
import com.autocompany.service.ClarificationSessionService;
import com.autocompany.service.RequirementSetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClarificationController.class)
public class ClarificationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClarificationSessionService sessionService;

    @MockBean
    private ClarificationQuestionService questionService;

    @MockBean
    private RequirementSetService requirementSetService;

    private ClarificationSession sampleSession(int number, SessionStatus status) {
        UUID projectId = UUID.randomUUID();
        ClarificationSession session = ClarificationSession.builder()
                .project(Project.builder().id(projectId).name("블로그 사이트").build())
                .goalText("블로그 사이트 만들어줘")
                .sessionNumber(number)
                .status(status)
                .openedByEmployeeId(UUID.randomUUID())
                .build();
        session.setId(UUID.randomUUID());
        return session;
    }

    @Test
    public void goalSubmissionReturns201WithSession() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.openPipelineSession(eq(projectId), any(), eq("블로그 사이트 만들어줘"), any()))
                .thenReturn(sampleSession(1, SessionStatus.OPEN));

        mockMvc.perform(post("/api/v1/projects/{projectId}/goal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"goalText\":\"블로그 사이트 만들어줘\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionNumber").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    public void goalSubmissionWithAttachmentsReturnsParsedRefs() throws Exception {
        UUID projectId = UUID.randomUUID();
        ClarificationSession session = sampleSession(1, SessionStatus.OPEN);
        session.setAttachments(java.util.List.of(new ClarificationSession.AttachmentRef(
                UUID.randomUUID(), "text/markdown", "요구사항.md", 42L, "PARSED")));
        when(sessionService.openPipelineSession(eq(projectId), any(), eq("블로그 사이트 만들어줘"), any()))
                .thenReturn(session);
        String contentBase64 = java.util.Base64.getEncoder().encodeToString("회원가입, 로그인".getBytes());

        mockMvc.perform(post("/api/v1/projects/{projectId}/goal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"goalText\":\"블로그 사이트 만들어줘\","
                                + "\"attachments\":[{\"filename\":\"요구사항.md\",\"mediaType\":\"text/markdown\","
                                + "\"contentBase64\":\"" + contentBase64 + "\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.attachments[0].filename").value("요구사항.md"));
    }

    @Test
    public void goalSubmissionMissingFieldsReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.openPipelineSession(eq(projectId), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("goalText or attachments is required (§28.7: min 1 of 2, 400)"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/goal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void goalSubmissionNeitherTextNorAttachmentsReturns400() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.openPipelineSession(eq(projectId), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("goalText or attachments is required (§28.7: min 1 of 2, 400)"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/goal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}")) // §28.7(1) 둘 다 비면 400
                .andExpect(status().isBadRequest());
    }

    @Test
    public void duplicateActiveGoalReturns409() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.openPipelineSession(eq(projectId), any(), any(), any()))
                .thenThrow(new IllegalStateException("Active clarification session already exists"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/goal", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"goalText\":\"블로그 사이트 만들어줘\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void getSessionsForProjectReturnsList() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.getSessionsByProject(projectId))
                .thenReturn(List.of(sampleSession(1, SessionStatus.WAITING_CLIENT)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/clarification-sessions", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING_CLIENT"));
    }

    @Test
    public void getSessionReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionService.getSession(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/clarification-sessions/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    public void pendingSessionsInboxEndpointReturnsList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(sessionService.getPendingSessionsForUser(userId))
                .thenReturn(List.of(sampleSession(1, SessionStatus.WAITING_CLIENT)));

        mockMvc.perform(get("/api/v1/users/{userId}/pending-sessions", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goalText").value("블로그 사이트 만들어줘"));
    }

    @Test
    public void pendingSessionsInboxEndpointEmptyWhenNoQueue() throws Exception {
        when(sessionService.getPendingSessionsForUser(any(UUID.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/{userId}/pending-sessions", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ==================== REQ-005 사용자 개설·취소(§28.6) ====================

    @Test
    public void userSessionOpenReturns201WithIncrementedNumber() throws Exception {
        UUID projectId = UUID.randomUUID();
        ClarificationSession session = sampleSession(2, SessionStatus.OPEN);
        session.setOpenedByUserId(UUID.randomUUID());
        when(sessionService.openUserSession(eq(projectId), any(), eq("요금제 문구 수정"), any()))
                .thenReturn(session);

        mockMvc.perform(post("/api/v1/projects/{projectId}/clarification-sessions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"changeNote\":\"요금제 문구 수정\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionNumber").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    public void userSessionOpenWhileActiveReturns409() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(sessionService.openUserSession(eq(projectId), any(), any(), any()))
                .thenThrow(new IllegalStateException("Active clarification session already exists for project (§28.6: 409)"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/clarification-sessions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"changeNote\":\"수정\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void cancelSessionReturns200WithCancelledStatus() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.cancelSession(eq(sessionId), any()))
                .thenReturn(sampleSession(1, SessionStatus.CANCELLED));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/cancel", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    public void cancelFrozenSessionReturns409() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionService.cancelSession(eq(sessionId), any()))
                .thenThrow(new IllegalStateException("Frozen session cannot be cancelled (§28.6)"));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/cancel", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }
}
