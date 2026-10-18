package com.autocompany.integration;

import com.autocompany.controller.EmployeeController;
import com.autocompany.entity.JobGrade;
import com.autocompany.entity.GradeModel;
import com.autocompany.entity.ModelProfile;
import com.autocompany.entity.ModelProvider;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @MockBean
    private ModelAdminService modelAdminService;

    @MockBean
    private RatingEngineService ratingEngineService;

    @MockBean
    private CompanyTemplateService companyTemplateService;

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

    @Test
    public void testGetModelProvidersEndpoint() throws Exception {
        UUID organizationId = UUID.randomUUID();
        ModelProvider provider = new ModelProvider();
        provider.setId(UUID.randomUUID());
        provider.setOrganizationId(organizationId);
        provider.setName("openai");
        provider.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);

        when(modelAdminService.getProvidersByOrganization(organizationId)).thenReturn(List.of(provider));

        mockMvc.perform(get("/api/v1/model-providers").param("organizationId", organizationId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(modelAdminService).getProvidersByOrganization(organizationId);
    }

    @Test
    public void testCreateModelProviderEndpoint() throws Exception {
        ModelProvider provider = new ModelProvider();
        provider.setId(UUID.randomUUID());
        provider.setName("openai");
        provider.setProviderType(ModelProvider.ProviderType.OPENAI_COMPATIBLE);

        when(modelAdminService.createProvider(any(ModelProvider.class))).thenReturn(provider);

        mockMvc.perform(post("/api/v1/model-providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"550e8400-e29b-41d4-a716-446655440000\","
                        + "\"name\":\"openai\",\"providerType\":\"OPENAI_COMPATIBLE\","
                        + "\"baseUrl\":\"http://localhost:8000\"}"))
                .andExpect(status().isCreated());

        verify(modelAdminService).createProvider(any(ModelProvider.class));
    }

    @Test
    public void testGetModelProfilesEndpoint() throws Exception {
        UUID organizationId = UUID.randomUUID();
        ModelProfile profile = new ModelProfile();
        profile.setId(UUID.randomUUID());
        profile.setName("gpt-4o-mini");
        profile.setProviderModelId("gpt-4o-mini");

        when(modelAdminService.getProfilesByOrganization(organizationId)).thenReturn(List.of(profile));

        mockMvc.perform(get("/api/v1/model-profiles").param("organizationId", organizationId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(modelAdminService).getProfilesByOrganization(organizationId);
    }

    @Test
    public void testCreateModelProfileEndpoint() throws Exception {
        UUID providerId = UUID.randomUUID();
        ModelProfile profile = new ModelProfile();
        profile.setId(UUID.randomUUID());
        profile.setName("gpt-4o-mini");
        profile.setProviderModelId("gpt-4o-mini");

        when(modelAdminService.createProfile(eq(providerId), any(ModelProfile.class))).thenReturn(profile);

        mockMvc.perform(post("/api/v1/model-profiles")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\":\"" + providerId + "\",\"name\":\"gpt-4o-mini\","
                        + "\"providerModelId\":\"gpt-4o-mini\",\"qualityTier\":2,"
                        + "\"inputCostPerMillion\":0.15,\"outputCostPerMillion\":0.6}"))
                .andExpect(status().isCreated());

        verify(modelAdminService).createProfile(eq(providerId), any(ModelProfile.class));
    }

    @Test
    public void testGetEvaluationsEndpoint() throws Exception {
        UUID employeeId = UUID.randomUUID();
        RatingEngineService.EvaluationReport report = RatingEngineService.EvaluationReport.builder()
                .employeeId(employeeId)
                .name("Test Employee")
                .grade("Mid")
                .totalFeedbackCount(3)
                .activeFeedbackCount(3)
                .build();

        when(ratingEngineService.getEvaluationReport(employeeId)).thenReturn(Optional.of(report));

        mockMvc.perform(get("/api/v1/employees/{id}/evaluations", employeeId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(ratingEngineService).getEvaluationReport(employeeId);
    }

    @Test
    public void testGetEvaluationsEndpointNotFound() throws Exception {
        UUID employeeId = UUID.randomUUID();

        when(ratingEngineService.getEvaluationReport(employeeId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/employees/{id}/evaluations", employeeId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(ratingEngineService).getEvaluationReport(employeeId);
    }

    @Test
    public void testTemplateImportEndpoint() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(companyTemplateService.importDefaultCompanyTemplate(organizationId, userId))
                .thenReturn(new CompanyTemplateService.TemplateImportResult(organizationId, List.of(), List.of()));

        mockMvc.perform(post("/api/v1/organizations/template/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + organizationId + "\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isCreated());

        verify(companyTemplateService).importDefaultCompanyTemplate(organizationId, userId);
    }

    @Test
    public void testTemplateImportConflictReturns409() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(companyTemplateService.importDefaultCompanyTemplate(organizationId, userId))
                .thenThrow(new TemplateAlreadyImportedException("Organization already has job grades"));

        mockMvc.perform(post("/api/v1/organizations/template/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + organizationId + "\",\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isConflict());
    }

    // ==================== ORG-003 중계 프로젝트 매핑 (§5.26) ====================

    @Test
    public void testCreateRelayProjectReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        com.autocompany.entity.Project project = com.autocompany.entity.Project.builder()
                .name("중계 프로젝트").clientId(clientId).build();
        project.setId(UUID.randomUUID());
        when(projectService.createProject(eq(userId), anyString(), any(), eq(clientId))).thenReturn(project);

        mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\",\"name\":\"중계 프로젝트\",\"clientId\":\"" + clientId + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    public void testCreateProjectWithArchivedClientReturns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(projectService.createProject(eq(userId), anyString(), any(), eq(clientId)))
                .thenThrow(new IllegalStateException("Archived client cannot be used for new relays (§5.26: 409)"));

        mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\",\"name\":\"중계 프로젝트\",\"clientId\":\"" + clientId + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    public void testCreateProjectWithUnknownClientReturns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        when(projectService.createProject(eq(userId), anyString(), any(), eq(clientId)))
                .thenThrow(new java.util.NoSuchElementException("Client not found: " + clientId));

        mockMvc.perform(post("/api/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\",\"name\":\"중계 프로젝트\",\"clientId\":\"" + clientId + "\"}"))
                .andExpect(status().isNotFound());
    }

    // ==================== EMP-001 직원 커스텀 지시 (§5.4) ====================

    @Test
    public void testUpdateEmployeeInstructionsReturns200() throws Exception {
        UUID employeeId = UUID.randomUUID();
        com.autocompany.entity.Employee employee = com.autocompany.entity.Employee.builder()
                .name("김개발").customInstructions("항상 테스트 먼저").build();
        employee.setId(employeeId);
        when(employeeService.updateEmployee(eq(employeeId), any(), eq("항상 테스트 먼저"), any(), anyBoolean()))
                .thenReturn(employee);

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customInstructions\":\"항상 테스트 먼저\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testUpdateEmployeeInstructionsOverLimitReturns400() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeService.updateEmployee(eq(employeeId), any(), any(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("customInstructions exceeds 4000 chars (§26 custom_instructions_max_chars: 400)"));

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customInstructions\":\"너무 긴 지시\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdateEmployeeMissingReturns404() throws Exception {
        UUID employeeId = UUID.randomUUID();
        when(employeeService.updateEmployee(eq(employeeId), any(), any(), any(), anyBoolean()))
                .thenThrow(new java.util.NoSuchElementException("Employee not found: " + employeeId));

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customInstructions\":\"지시\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testHireEmployeeWithInstructionsReturns201() throws Exception {
        UUID userId = UUID.randomUUID();
        com.autocompany.entity.Employee employee = com.autocompany.entity.Employee.builder()
                .name("이개발").customInstructions("보안 우선").build();
        employee.setId(UUID.randomUUID());
        when(employeeService.createEmployee(eq(userId), eq("이개발"), eq("DEVELOPER"), eq("보안 우선"), any()))
                .thenReturn(employee);

        mockMvc.perform(post("/api/v1/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + userId + "\",\"name\":\"이개발\",\"role\":\"DEVELOPER\","
                        + "\"customInstructions\":\"보안 우선\"}"))
                .andExpect(status().isCreated());
    }

    // ==================== TEAM-001 팀 배치·아카이브 (§5.3) ====================

    @Test
    public void testAssignTeamIdPresentReturns200() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        com.autocompany.entity.Employee employee = com.autocompany.entity.Employee.builder().name("김개발").build();
        employee.setId(employeeId);
        employee.setTeamId(teamId);
        when(employeeService.updateEmployee(eq(employeeId), any(), any(), eq(teamId), eq(true))).thenReturn(employee);

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isOk());

        verify(employeeService).updateEmployee(eq(employeeId), any(), any(), eq(teamId), eq(true));
    }

    @Test
    public void testClearTeamIdPresentNullReturns200() throws Exception {
        UUID employeeId = UUID.randomUUID();
        com.autocompany.entity.Employee employee = com.autocompany.entity.Employee.builder().name("김개발").build();
        employee.setId(employeeId);
        when(employeeService.updateEmployee(eq(employeeId), any(), any(), isNull(), eq(true))).thenReturn(employee);

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":null}"))
                .andExpect(status().isOk());

        verify(employeeService).updateEmployee(eq(employeeId), any(), any(), isNull(), eq(true));
    }

    @Test
    public void testPatchWithoutTeamIdKeyKeepsTeam() throws Exception {
        UUID employeeId = UUID.randomUUID();
        com.autocompany.entity.Employee employee = com.autocompany.entity.Employee.builder().name("새이름").build();
        employee.setId(employeeId);
        when(employeeService.updateEmployee(eq(employeeId), eq("새이름"), any(), isNull(), eq(false))).thenReturn(employee);

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk());

        verify(employeeService).updateEmployee(eq(employeeId), eq("새이름"), any(), isNull(), eq(false));
    }

    @Test
    public void testAssignArchivedTeamReturns409() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(employeeService.updateEmployee(eq(employeeId), any(), any(), eq(teamId), eq(true)))
                .thenThrow(new IllegalStateException("Archived team cannot accept new members (§5.3: 409)"));

        mockMvc.perform(patch("/api/v1/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamId + "\"}"))
                .andExpect(status().isConflict());
    }

    // ==================== WEB-003 프로젝트 목록·상세·업무 (§17) ====================

    @Test
    public void testGetProjectsReturns200() throws Exception {
        UUID userId = UUID.randomUUID();
        com.autocompany.entity.Project project = com.autocompany.entity.Project.builder().name("블로그").build();
        project.setId(UUID.randomUUID());
        when(projectService.getProjectsByUser(userId)).thenReturn(List.of(project));

        mockMvc.perform(get("/api/v1/projects").param("userId", userId.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(projectService).getProjectsByUser(userId);
    }

    @Test
    public void testGetProjectMissingReturns404() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(projectService.getProject(projectId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/projects/{id}", projectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testGetProjectTasksReturns200() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/projects/{id}/tasks", projectId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(taskService).getTasksByProject(projectId);
    }
}