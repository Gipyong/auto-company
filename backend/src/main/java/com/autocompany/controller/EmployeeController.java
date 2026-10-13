package com.autocompany.controller;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.TaskType;
import com.autocompany.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final CostPerformanceRouterService routerService;

    // 직원 채용
    @PostMapping("/employees")
    public ResponseEntity<Employee> hireEmployee(@RequestBody HireRequest request) {
        Employee employee = employeeService.createEmployee(request.userId, request.name, request.role);
        return ResponseEntity.status(201).body(employee);
    }

    // 직원 목록
    @GetMapping("/employees")
    public ResponseEntity<List<Employee>> getEmployees(@RequestParam UUID userId) {
        return ResponseEntity.ok(employeeService.getEmployeesByUserId(userId));
    }

    // 직원 상세
    @GetMapping("/employees/{id}")
    public ResponseEntity<Employee> getEmployee(@PathVariable UUID id) {
        return employeeService.getEmployee(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 직원 모델 변경
    @PatchMapping("/employees/{id}/model")
    public ResponseEntity<Void> updateModel(@PathVariable UUID id, @RequestBody ModelUpdate request) {
        employeeService.updateEmployeeModel(id, request.aiModel);
        return ResponseEntity.ok().build();
    }

    // 프로젝트 생성
    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(@RequestBody ProjectRequest request) {
        Project project = projectService.createProject(request.userId, request.name, request.description);
        return ResponseEntity.status(201).body(project);
    }

    // 업무 지시
    @PostMapping("/tasks")
    public ResponseEntity<Task> assignTask(@RequestBody TaskRequest request) {
        Task task = taskService.assignTask(request.projectId, request.assigneeId, 
                request.taskType, request.title, request.prompt);
        return ResponseEntity.status(202).body(task);
    }

    // 업무 상태
    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable UUID id) {
        return taskService.getTask(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 동료 평가
    @PostMapping("/ratings")
    public ResponseEntity<PeerRating> submitRating(@RequestBody RatingRequest request) {
        Task task = taskService.getTask(request.taskId).orElseThrow();
        PeerRating rating = taskService.submitRating(task, request.raterId, 
                request.ratedId, request.category, request.score, request.comment);
        return ResponseEntity.status(201).body(rating);
    }

    // 토론 메시지 전송
    @PostMapping("/messages")
    public ResponseEntity<DiscussionMessage> sendMessage(@RequestBody MessageRequest request) {
        Project project = projectService.getProject(request.projectId).orElseThrow();
        DiscussionMessage message = taskService.sendMessage(project, 
                request.senderEmployeeId, request.targetEmployeeId, 
                request.content, request.messageType);
        return ResponseEntity.status(201).body(message);
    }

    // CEO Ask Anything (RAG)
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> askQuestion(@RequestBody AskRequest request) {
        // RAG 시스템 연동 (나중에 구현)
        return ResponseEntity.ok(new AskResponse(
                "질문: " + request.question,
                "답변: RAG 시스템이 이 질문에 대해 검색 후 답변합니다.",
                List.of()
        ));
    }

    // Request DTOs
    public static class HireRequest {
        public UUID userId;
        public String name;
        public String role;
    }

    public static class ModelUpdate {
        public String aiModel;
    }

    public static class ProjectRequest {
        public UUID userId;
        public String name;
        public String description;
    }

    public static class TaskRequest {
        public UUID projectId;
        public UUID assigneeId;
        public TaskType taskType;
        public String title;
        public String prompt;
    }

    public static class RatingRequest {
        public UUID taskId;
        public UUID raterId;
        public UUID ratedId;
        public String category;
        public float score;
        public String comment;
    }

    public static class MessageRequest {
        public UUID projectId;
        public UUID senderEmployeeId;
        public UUID targetEmployeeId;
        public String content;
        public String messageType;
    }

    public static class AskRequest {
        public String question;
    }

    public static class AskResponse {
        public String question;
        public String answer;
        public List<String> sources;
        
        public AskResponse(String question, String answer, List<String> sources) {
            this.question = question;
            this.answer = answer;
            this.sources = sources;
        }
    }
}