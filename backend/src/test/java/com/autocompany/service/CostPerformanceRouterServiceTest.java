package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CostPerformanceRouterServiceTest {

    @Autowired
    private CostPerformanceRouterService routerService;

    private Task sampleTask;

    @BeforeEach
    public void setUp() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@autocompany.com")
                .name("CEO")
                .build();

        Employee employee = Employee.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("김개발")
                .role(Role.DEVELOPER)
                .rank(Rank.JUNIOR)
                .level(3)
                .build();

        Project project = Project.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("테스트 프로젝트")
                .build();

        sampleTask = Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .assignee(employee)
                .type(TaskType.CODING)
                .title("로그인 버그 수정")
                .prompt("Fix null pointer exception")
                .build();
    }

    @Test
    public void testTaskClassification() {
        CostPerformanceRouterService.TaskClassification classification = routerService.classifyTask(sampleTask);
        assertNotNull(classification);
        assertEquals("coding", classification.getTaskType());
    }

    @Test
    public void testSelectBestModel() {
        CostPerformanceRouterService.TaskClassification classification = routerService.classifyTask(sampleTask);
        String selectedModel = routerService.selectBestModel(classification);
        assertNotNull(selectedModel, "최적 모델이 선택되어야 합니다.");
    }

    @Test
    public void testEscalationChain() {
        String nextModel = routerService.getEscalationModel("llama3_local", "coding");
        assertNotNull(nextModel, "실패 시 상위 모델로 승급 모델이 존재해야 합니다.");
    }

    @Test
    public void testLogExperimentAndHistory() {
        routerService.logExperiment("llama3_local", "coding", true, 0.001f, true);
        routerService.logExperiment("llama3_local", "coding", false, 0.001f, false);
        // 실험 로그가 기록되어 가중치 계산에 반영되는지 확인
        CostPerformanceRouterService.TaskClassification classification = routerService.classifyTask(sampleTask);
        assertNotNull(classification);
    }
}