package com.autocompany.integration;

import com.autocompany.controller.ClarificationController;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.Project;
import com.autocompany.entity.RequirementSet;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REQ-003 API 테스트(§17): 동결 201/상태 게이트 409/소유자 아님 400, 누적 기록 조회.
 * frozen 재동결은 409와 "새 세션(REQ-005)" 안내 메시지를 확인한다.
 */
@WebMvcTest(ClarificationController.class)
public class ClarificationRequirementApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClarificationSessionService sessionService;

    @MockBean
    private ClarificationQuestionService questionService;

    @MockBean
    private RequirementSetService requirementSetService;

    private RequirementSet frozenSet(int version) {
        ClarificationSession session = ClarificationSession.builder()
                .project(Project.builder().id(UUID.randomUUID()).name("블로그 사이트").build())
                .goalText("골")
                .sessionNumber(1)
                .status(SessionStatus.FROZEN)
                .build();
        session.setId(UUID.randomUUID());
        RequirementSet set = RequirementSet.builder()
                .project(session.getProject())
                .session(session)
                .version(version)
                .status(RequirementSet.SetStatus.FROZEN)
                .contentJson("{\"functional\":[],\"nonFunctional\":[],\"constraints\":[],\"outOfScope\":[],\"openRisks\":[]}")
                .build();
        set.setId(UUID.randomUUID());
        return set;
    }

    private RequirementSetService.RequirementContent emptyContent() {
        return new RequirementSetService.RequirementContent(
                List.of(new RequirementSetService.RequirementItem("초기 목표", "블로그", "client_declared", "goal_text", null)),
                List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void freezeReturns201WithFrozenRequirementSet() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RequirementSet set = frozenSet(1);
        when(requirementSetService.freeze(eq(sessionId), eq(userId)))
                .thenReturn(new RequirementSetService.FrozenOutcome(set, emptyContent()));
        when(requirementSetService.parseContent(set)).thenReturn(emptyContent());

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/freeze", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.content.functional[0].source").value("client_declared"));
    }

    @Test
    void freezeOnFrozenSessionReturns409() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(requirementSetService.freeze(eq(sessionId), eq(userId)))
                .thenThrow(new IllegalStateException("Session already frozen (§28.6) — declare a new session for changes (REQ-005)"));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/freeze", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void freezeByNonOwnerReturns400() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(requirementSetService.freeze(eq(sessionId), eq(userId)))
                .thenThrow(new IllegalArgumentException("Only the project owner (client position) can freeze"));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/freeze", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void freezeMissingSessionReturns404() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(requirementSetService.freeze(eq(sessionId), eq(userId)))
                .thenThrow(new NoSuchElementException("Clarification session not found: " + sessionId));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/freeze", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requirementsReturnsVersionHistoryDescending() throws Exception {
        UUID projectId = UUID.randomUUID();
        List<RequirementSet> sets = List.of(frozenSet(2), frozenSet(1));
        when(requirementSetService.listByProject(projectId)).thenReturn(sets);
        when(requirementSetService.parseContent(sets.get(0))).thenReturn(emptyContent());
        when(requirementSetService.parseContent(sets.get(1))).thenReturn(emptyContent());

        mockMvc.perform(get("/api/v1/projects/{projectId}/requirements", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
    }
}
