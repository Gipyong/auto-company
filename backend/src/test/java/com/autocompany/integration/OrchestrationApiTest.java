package com.autocompany.integration;

import com.autocompany.controller.OrchestrationController;
import com.autocompany.service.OrchestrationService;
import com.autocompany.service.OrchestrationService.CycleView;
import com.autocompany.service.OrchestrationService.LatestFrozen;
import com.autocompany.service.OrchestrationService.OrchestrationView;
import com.autocompany.service.OrchestrationService.TaskView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrchestrationController.class)
public class OrchestrationApiTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private OrchestrationService orchestrationService;

    @Test
    public void triggerReturns201WithCycle() throws Exception {
        UUID setId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(orchestrationService.trigger(setId, userId)).thenReturn(new CycleView(
                UUID.randomUUID(), setId, 1, "FIRST", 7, 0, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/requirements/{id}/trigger", setId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("FIRST"))
                .andExpect(jsonPath("$.requirementSetVersion").value(1))
                .andExpect(jsonPath("$.createdTaskCount").value(7))
                .andExpect(jsonPath("$.blockedTaskCount").value(0));

        verify(orchestrationService).trigger(setId, userId);
    }

    @Test
    public void triggerWithoutBodyCallsServiceWithNullUser() throws Exception {
        UUID setId = UUID.randomUUID();
        when(orchestrationService.trigger(setId, null)).thenReturn(new CycleView(
                UUID.randomUUID(), setId, 1, "FIRST", 4, 0, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/requirements/{id}/trigger", setId))
                .andExpect(status().isCreated());

        verify(orchestrationService).trigger(eq(setId), isNull());
    }

    @Test
    public void triggerNotFrozenReturns409() throws Exception {
        UUID setId = UUID.randomUUID();
        when(orchestrationService.trigger(setId, null))
                .thenThrow(new IllegalStateException("NOT_FROZEN: 동결된 요구사항 세트만 분해할 수 있습니다(§29.1)"));

        mockMvc.perform(post("/api/v1/requirements/{id}/trigger", setId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ORCHESTRATION_CONFLICT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("NOT_FROZEN")));
    }

    @Test
    public void triggerAlreadyDecomposedReturns409() throws Exception {
        UUID setId = UUID.randomUUID();
        when(orchestrationService.trigger(setId, null))
                .thenThrow(new IllegalStateException("ALREADY_DECOMPOSED: 이미 분해된 요구 세트입니다(§5.27)"));

        mockMvc.perform(post("/api/v1/requirements/{id}/trigger", setId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("ORCHESTRATION_CONFLICT"));
    }

    @Test
    public void triggerMissingSetReturns404() throws Exception {
        UUID setId = UUID.randomUUID();
        when(orchestrationService.trigger(setId, null))
                .thenThrow(new NoSuchElementException("Requirement set not found: " + setId));

        mockMvc.perform(post("/api/v1/requirements/{id}/trigger", setId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }

    @Test
    public void summaryReturnsCycleTasksAndLatestFrozen() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        when(orchestrationService.summary(projectId)).thenReturn(new OrchestrationView(
                new CycleView(UUID.randomUUID(), setId, 1, "FIRST", 4, 1, LocalDateTime.now()),
                List.of(
                        new TaskView(UUID.randomUUID(), null, "EPIC", "v1 요구사항 구현", "READY", null, null, null),
                        new TaskView(UUID.randomUUID(), null, "TASK", "구현 — 주문 등록", "BLOCKED",
                                assigneeId, "김개발", "배정 가능한 직원 없음 — TEST 역량 직급/직원 필요(§29.3)")),
                new LatestFrozen(setId, 1, true)));

        mockMvc.perform(get("/api/v1/projects/{id}/orchestration", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle.mode").value("FIRST"))
                .andExpect(jsonPath("$.cycle.blockedTaskCount").value(1))
                .andExpect(jsonPath("$.tasks[0].type").value("EPIC"))
                .andExpect(jsonPath("$.tasks[0].assigneeId").value(nullValue()))
                .andExpect(jsonPath("$.tasks[1].blockedReason")
                        .value(org.hamcrest.Matchers.containsString("배정 가능한 직원 없음")))
                .andExpect(jsonPath("$.latestFrozen.version").value(1))
                .andExpect(jsonPath("$.latestFrozen.decomposed").value(true));
    }

    @Test
    public void summaryWithoutCycleReturns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(orchestrationService.summary(projectId))
                .thenReturn(new OrchestrationView(null, List.of(), null));

        mockMvc.perform(get("/api/v1/projects/{id}/orchestration", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycle").value(nullValue()))
                .andExpect(jsonPath("$.tasks").isEmpty())
                .andExpect(jsonPath("$.latestFrozen").value(nullValue()));
    }

    @Test
    public void summaryMissingProjectReturns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(orchestrationService.summary(projectId))
                .thenThrow(new NoSuchElementException("Project not found: " + projectId));

        mockMvc.perform(get("/api/v1/projects/{id}/orchestration", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code").value("NOT_FOUND"));
    }
}
