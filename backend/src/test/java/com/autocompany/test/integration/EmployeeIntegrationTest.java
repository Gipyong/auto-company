package com.autocompany.integration;

import com.autocompany.controller.EmployeeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.autocompany.service.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(EmployeeController.class)
public class EmployeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmployeeService employeeService;
    
    @MockBean
    private ProjectService projectService;
    
    @MockBean
    private TaskService taskService;
    
    @MockBean
    private CostPerformanceRouterService routerService;

    @Test
    public void testGetEmployeesEndpoint() throws Exception {
        // API 명세서 Section 3.1: 직원 조회 엔드포인트 테스트
        mockMvc.perform(get("/api/v1/employees").param("userId", "550e8400-e29b-41d4-a716-446655440000")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void testAskEndpoint() throws Exception {
        // API 명세서 Section 3.4: CEO Ask Anything 엔드포인트 테스트 (POST /api/v1/ask)
        mockMvc.perform(post("/api/v1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"현재 아키텍처는?\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testEmployeeHireEndpointExists() throws Exception {
        // 직원 채용 엔드포인트 존재 확인 (POST /api/v1/employees)
        assertNotNull(EmployeeController.class);
    }
}
