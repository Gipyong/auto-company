package com.autocompany.integration;

import com.autocompany.controller.ClarificationController;
import com.autocompany.entity.ClarificationQuestion;
import com.autocompany.entity.ClarificationQuestion.QuestionType;
import com.autocompany.entity.ClarificationSession;
import com.autocompany.entity.ClarificationSession.SessionStatus;
import com.autocompany.entity.Project;
import com.autocompany.entity.User;
import com.autocompany.service.ClarificationQuestionService;
import com.autocompany.service.ClarificationQuestionService.RemainingAnswerView;
import com.autocompany.service.ClarificationQuestionService.RemainingAnswersException;
import com.autocompany.service.ClarificationSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
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
public class ClarificationQuestionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClarificationSessionService sessionService;

    @MockBean
    private ClarificationQuestionService questionService;

    private ClarificationQuestion sampleQuestion(int order) {
        UUID sessionId = UUID.randomUUID();
        ClarificationSession session = ClarificationSession.builder()
                .project(Project.builder().id(UUID.randomUUID()).name("서비스").build())
                .goalText("골")
                .sessionNumber(1)
                .status(SessionStatus.WAITING_CLIENT)
                .build();
        session.setId(sessionId);
        ClarificationQuestion q = ClarificationQuestion.builder()
                .questionType(QuestionType.SINGLE_CHOICE)
                .prompt("이번 결과물을 어느 수준에서 먼저 확정하고 싶으신가요?")
                .allowOther(true)
                .options(List.of(new ClarificationQuestion.QuestionOption("mvp", "MVP로 시작", null)))
                .displayOrder(order)
                .build();
        q.setSession(session);
        q.setId(UUID.randomUUID());
        return q;
    }

    @Test
    public void composeQuestionsReturns201() throws Exception {
        UUID sessionId = UUID.randomUUID();
        ClarificationQuestion q = sampleQuestion(1);
        when(sessionService.getSession(sessionId)).thenReturn(Optional.of(q.getSession()));
        when(questionService.composeQuestions(sessionId)).thenReturn(List.of(q));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/questions", sessionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].questionType").value("SINGLE_CHOICE"))
                .andExpect(jsonPath("$[0].options[0].value").value("mvp"));
    }

    @Test
    public void composeQuestionsDuplicateReturns409() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(questionService.composeQuestions(sessionId))
                .thenThrow(new IllegalStateException("Questions already composed"));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/questions", sessionId))
                .andExpect(status().isConflict());
    }

    @Test
    public void getQuestionsReturnsList() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(questionService.getQuestions(sessionId)).thenReturn(List.of(sampleQuestion(1)));

        mockMvc.perform(get("/api/v1/clarification-sessions/{id}/questions", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayOrder").value(1));
    }

    @Test
    public void submitAnswersReturns200WithSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(questionService.submitAnswers(eq(sessionId), eq(userId), any()))
                .thenAnswer(inv -> {
                    ClarificationSession session = ClarificationSession.builder()
                            .project(Project.builder().id(UUID.randomUUID()).build())
                            .goalText("골")
                            .sessionNumber(1)
                            .status(SessionStatus.REVIEW)
                            .build();
                    session.setId(sessionId);
                    session.getProject().setUser(User.builder().id(userId).email("u@example.com").name("u").build());
                    return session;
                });

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/answers", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"answers\":[{"
                                + "\"questionId\":\"" + UUID.randomUUID() + "\","
                                + "\"selectedValues\":[\"mvp\"]}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"));
    }

    @Test
    public void submitAnswersMissingReturns409WithRemaining() throws Exception {
        UUID sessionId = UUID.randomUUID();
        RemainingAnswersException e = new RemainingAnswersException(List.of(
                new RemainingAnswerView(UUID.randomUUID(), 3, "남은 질문", "SHORT_TEXT", "UNANSWERED")));
        when(questionService.submitAnswers(eq(sessionId), any(), any())).thenThrow(e);

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/answers", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"answers\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code").value("REQUIRED_ANSWERS_MISSING"))
                .andExpect(jsonPath("$.remaining[0].prompt").value("남은 질문"));
    }

    @Test
    public void submitAnswersInvalidReturns400() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(questionService.submitAnswers(eq(sessionId), any(), any()))
                .thenThrow(new IllegalArgumentException("Unknown option value"));

        mockMvc.perform(post("/api/v1/clarification-sessions/{id}/answers", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"answers\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
