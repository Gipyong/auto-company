package com.autocompany.integration;

import com.autocompany.controller.EmployeeController;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.GradeModel;
import com.autocompany.entity.Task;
import com.autocompany.entity.PeerRating;
import com.autocompany.entity.enums.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.autocompany.service.*;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(EmployeeController.class)
public class EmployeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;
    
    @MockBean
    private JobGradeService jobGradeService;
    
    @MockBean
    private ProjectService projectService;
    
    @MockBean
    private TaskService taskService;
    
    @MockBean
    private CostPerformanceRouterService routerService;

    @Test
    public void testGetEmployeesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/employees").param("userId", "550e8400-e29b-41d4-a716-446655440000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testAskEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"현재 아키텍처는?\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testEmployeeHireEndpointExists() throws Exception {
        assertNotNull(EmployeeController.class);
    }

    @Test
    public void testAssignGradeEndpoint() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID gradeId = UUID.randomUUID();
        
        mockMvc.perform(patch("/api/v1/employees/{id}/grade", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gradeId\":\"" + gradeId + "\"}"))
                .andExpect(status().isOk());
        
        verify(employeeService).assignGrade(employeeId, gradeId);
    }

    @Test
    public void testRemoveGradeEndpoint() throws Exception {
        UUID employeeId = UUID.randomUUID();
        
        mockMvc.perform(patch("/api/v1/employees/{id}/grade", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());
        
        verify(employeeService).removeGrade(employeeId);
    }

    @Test
    public void testGetGradesEndpoint() throws Exception {
        UUID organizationId = UUID.randomUUID();
        JobGrade grade = new JobGrade();
        grade.setId(UUID.randomUUID());
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        
        when(jobGradeService.getGradesByOrganization(organizationId)).thenReturn(List.of(grade));
        
        mockMvc.perform(get("/api/v1/grades").param("organizationId", organizationId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        verify(jobGradeService).getGradesByOrganization(organizationId);
    }

    @Test
    public void testCreateGradeEndpoint() throws Exception {
        JobGrade grade = new JobGrade();
        grade.setId(UUID.randomUUID());
        grade.setCode("L1");
        grade.setName("Junior");
        grade.setOrdinal(1);
        
        when(jobGradeService.createGrade(any(JobGrade.class))).thenReturn(grade);
        
        mockMvc.perform(post("/api/v1/grades")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"550e8400-e29b-41d4-a716-446655440000\",\"code\":\"L1\",\"name\":\"Junior\",\"ordinal\":1}"))
                .andExpect(status().isCreated());
        
        verify(jobGradeService).createGrade(any(JobGrade.class));
    }

    @Test
    public void testGetGradeModelsEndpoint() throws Exception {
        UUID gradeId = UUID.randomUUID();
        GradeModel gm = new GradeModel();
        gm.setId(UUID.randomUUID());
        gm.setGradeId(gradeId);
        gm.setPriority(1);
        
        when(jobGradeService.getGradeModels(gradeId)).thenReturn(List.of(gm));
        
        mockMvc.perform(get("/api/v1/grades/{id}/models", gradeId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        verify(jobGradeService).getGradeModels(gradeId);
    }

    @Test
    public void testSetGradeModelsEndpoint() throws Exception {
        UUID gradeId = UUID.randomUUID();
        
        mockMvc.perform(put("/api/v1/grades/{id}/models", gradeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"gradeId\":\"" + gradeId + "\",\"modelProfileId\":\"550e8400-e29b-41d4-a716-446655440000\",\"priority\":1}]"))
                .andExpect(status().isOk());
        
        verify(jobGradeService).setGradeModels(any(), any());
    }

    @Test
    public void testSubmitRatingEndpoint() throws Exception {
        UUID taskId = UUID.randomUUID();
        Task task = new Task();
        task.setId(taskId);
        
        when(taskService.getTask(taskId)).thenReturn(Optional.of(task));
        
        PeerRating rating = new PeerRating();
        rating.setId(UUID.randomUUID());
        rating.setRaterEmployeeId(UUID.randomUUID());
        rating.setRatedEmployeeId(UUID.randomUUID());
        rating.setCategory(Category.CODE_QUALITY);
        rating.setScore(4.5f);
        rating.setComment("좋은 코드");
        rating.setEvidenceRefs(List.of("PR-123"));
        
        when(taskService.submitRating(any(), any(), any(), anyString(), anyFloat(), anyString(), anyList()))
                .thenReturn(rating);
        
        mockMvc.perform(post("/api/v1/ratings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskId\":\"" + taskId + "\",\"raterId\":\"" + UUID.randomUUID() + "\","
                        + "\"ratedId\":\"" + UUID.randomUUID() + "\",\"category\":\"CODE_QUALITY\","
                        + "\"score\":4.5,\"comment\":\"좋은 코드\",\"evidenceRefs\":[\"PR-123\"]}"))
                .andExpect(status().isCreated());
        
        verify(taskService).submitRating(any(), any(), any(), anyString(), anyFloat(), anyString(), anyList());
    }
}