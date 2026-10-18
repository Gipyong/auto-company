package com.autocompany.controller;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.TaskType;
import com.autocompany.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final JobGradeService jobGradeService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final CostPerformanceRouterService routerService;
    private final ModelAdminService modelAdminService;
    private final RatingEngineService ratingEngineService;
    private final CompanyTemplateService companyTemplateService;

    // ==================== Organization ====================

    // 기본 회사 템플릿 import (5단계 직급 + 기본 8인 조직, §6.1·§6.2)
    @PostMapping("/organizations/template/import")
    public ResponseEntity<CompanyTemplateService.TemplateImportResult> importDefaultTemplate(
            @RequestBody TemplateImportRequest request) {
        CompanyTemplateService.TemplateImportResult result =
                companyTemplateService.importDefaultCompanyTemplate(request.organizationId, request.userId);
        return ResponseEntity.status(201).body(result);
    }

    @ExceptionHandler(TemplateAlreadyImportedException.class)
    public ResponseEntity<Map<String, String>> handleTemplateAlreadyImported(TemplateAlreadyImportedException e) {
        return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
    }

    // §5.26 중계 연결 검증 — archived 클라이언트 등 상태 충돌 409
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Map.of("error_code", "STATE_CONFLICT", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("error_code", "INVALID_REQUEST", "message", e.getMessage()));
    }

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error_code", "NOT_FOUND", "message", e.getMessage()));
    }

    // ==================== Employee ====================
    
    // 직원 채용
    @PostMapping("/employees")
    public ResponseEntity<Employee> hireEmployee(@RequestBody HireRequest request) {
        Employee employee = employeeService.createEmployee(request.userId, request.name, request.role,
                request.customInstructions);
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

    // 직원 평가 리포트 (직급 변경 참고 자료, §15.4)
    @GetMapping("/employees/{id}/evaluations")
    public ResponseEntity<RatingEngineService.EvaluationReport> getEvaluations(@PathVariable UUID id) {
        return ratingEngineService.getEvaluationReport(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 직원 부분 갱신 (EMP-001 — 커스텀 지시 편집 §5.4)
    @PatchMapping("/employees/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable UUID id,
                                                   @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request.name, request.customInstructions));
    }

    // 직원 직급 배정/변경
    @PatchMapping("/employees/{id}/grade")
    public ResponseEntity<Void> assignGrade(@PathVariable UUID id, @RequestBody GradeAssignRequest request) {
        if (request.gradeId == null) {
            employeeService.removeGrade(id);
        } else {
            employeeService.assignGrade(id, request.gradeId);
        }
        return ResponseEntity.ok().build();
    }

    // ==================== JobGrade (직급) ====================

    // 직급 목록
    @GetMapping("/grades")
    public ResponseEntity<List<JobGrade>> getGrades(@RequestParam UUID organizationId) {
        return ResponseEntity.ok(jobGradeService.getGradesByOrganization(organizationId));
    }

    // 직급 생성
    @PostMapping("/grades")
    public ResponseEntity<JobGrade> createGrade(@RequestBody JobGrade grade) {
        JobGrade created = jobGradeService.createGrade(grade);
        return ResponseEntity.status(201).body(created);
    }

    // 직급 상세
    @GetMapping("/grades/{id}")
    public ResponseEntity<JobGrade> getGrade(@PathVariable UUID id) {
        return jobGradeService.getGrade(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 직급 수정
    @PatchMapping("/grades/{id}")
    public ResponseEntity<JobGrade> updateGrade(@PathVariable UUID id, @RequestBody JobGrade updated) {
        JobGrade grade = jobGradeService.updateGrade(id, updated);
        return ResponseEntity.ok(grade);
    }

    // 직급 삭제
    @DeleteMapping("/grades/{id}")
    public ResponseEntity<Void> deleteGrade(@PathVariable UUID id) {
        jobGradeService.deleteGrade(id);
        return ResponseEntity.ok().build();
    }

    // 직급별 가용 모델 풀 조회
    @GetMapping("/grades/{id}/models")
    public ResponseEntity<List<GradeModel>> getGradeModels(@PathVariable UUID id) {
        return ResponseEntity.ok(jobGradeService.getGradeModels(id));
    }

    // 직급별 가용 모델 풀 설정
    @PutMapping("/grades/{id}/models")
    public ResponseEntity<Void> setGradeModels(@PathVariable UUID id, @RequestBody List<GradeModel> models) {
        jobGradeService.setGradeModels(id, models);
        return ResponseEntity.ok().build();
    }

    // ==================== Models ====================

    // 조직의 provider 목록
    @GetMapping("/model-providers")
    public ResponseEntity<List<ModelProvider>> getModelProviders(@RequestParam UUID organizationId) {
        return ResponseEntity.ok(modelAdminService.getProvidersByOrganization(organizationId));
    }

    // provider 생성 (healthStatus는 UNKNOWN으로 시작)
    @PostMapping("/model-providers")
    public ResponseEntity<ModelProvider> createModelProvider(@RequestBody ModelProvider provider) {
        ModelProvider created = modelAdminService.createProvider(provider);
        return ResponseEntity.status(201).body(created);
    }

    // 조직의 모델 프로필 목록
    @GetMapping("/model-profiles")
    public ResponseEntity<List<ModelProfile>> getModelProfiles(@RequestParam UUID organizationId) {
        return ResponseEntity.ok(modelAdminService.getProfilesByOrganization(organizationId));
    }

    // 모델 프로필 생성
    @PostMapping("/model-profiles")
    public ResponseEntity<ModelProfile> createModelProfile(@RequestBody ProfileRequest request) {
        ModelProfile created = modelAdminService.createProfile(request.providerId, request.toProfile());
        return ResponseEntity.status(201).body(created);
    }

    // ==================== Project ====================
    
    @PostMapping("/projects")
    public ResponseEntity<Project> createProject(@RequestBody ProjectRequest request) {
        Project project = projectService.createProject(request.userId, request.name,
                request.description, request.clientId);
        return ResponseEntity.status(201).body(project);
    }

    // ==================== Task ====================
    
    @PostMapping("/tasks")
    public ResponseEntity<Task> assignTask(@RequestBody TaskRequest request) {
        Task task = taskService.assignTask(request.projectId, request.assigneeId, 
                request.taskType, request.title, request.prompt);
        return ResponseEntity.status(202).body(task);
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<Task> getTask(@PathVariable UUID id) {
        return taskService.getTask(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Rating ====================
    
    @PostMapping("/ratings")
    public ResponseEntity<PeerRating> submitRating(@RequestBody RatingRequest request) {
        Task task = taskService.getTask(request.taskId).orElseThrow();
        PeerRating rating = taskService.submitRating(task, request.raterId, 
                request.ratedId, request.category, request.score, request.comment, request.evidenceRefs);
        return ResponseEntity.status(201).body(rating);
    }

    // ==================== Message ====================
    
    @PostMapping("/messages")
    public ResponseEntity<DiscussionMessage> sendMessage(@RequestBody MessageRequest request) {
        Project project = projectService.getProject(request.projectId).orElseThrow();
        DiscussionMessage message = taskService.sendMessage(project, 
                request.senderEmployeeId, request.targetEmployeeId, 
                request.content, request.messageType);
        return ResponseEntity.status(201).body(message);
    }

    // ==================== CEO Ask ====================
    
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> askQuestion(@RequestBody AskRequest request) {
        return ResponseEntity.ok(new AskResponse(
                "질문: " + request.question,
                "답변: RAG 시스템이 이 질문에 대해 검색 후 답변합니다.",
                List.of()
        ));
    }

    // Request DTOs
    public static class TemplateImportRequest {
        public UUID organizationId;
        public UUID userId;
    }

    public static class HireRequest {
        public UUID userId;
        public String name;
        public String role;
        public String customInstructions; // §5.4 EMP-001(선택)
    }

    public static class EmployeeUpdateRequest {
        public String name;
        public String customInstructions; // §5.4 EMP-001 — null 유지, 빈 문자열 지우기
    }

    public static class GradeAssignRequest {
        public UUID gradeId;
    }

    public static class ProjectRequest {
        public UUID userId;
        public String name;
        public String description;
        public UUID clientId; // §5.26 외부 고객 중계 프로젝트(선택)
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
        public List<String> evidenceRefs; // PR, commit, test, doc references
    }

    public static class ProfileRequest {
        public UUID providerId;
        public String name;
        public String providerModelId;
        public List<String> capabilities;
        public Integer qualityTier;
        public BigDecimal inputCostPerMillion;
        public BigDecimal outputCostPerMillion;
        public Integer contextWindow;
        public Integer maxOutputTokens;
        public Boolean supportsStreaming;
        public Boolean supportsToolCalling;
        public Boolean supportsJsonSchema;
        public ModelProfile.LatencyClass latencyClass;
        public Boolean localOnly;
        public Boolean enabled;
        public Map<String, Object> metadata;

        public ModelProfile toProfile() {
            ModelProfile profile = new ModelProfile();
            profile.setName(name);
            profile.setProviderModelId(providerModelId);
            profile.setCapabilities(capabilities);
            profile.setQualityTier(qualityTier);
            profile.setInputCostPerMillion(inputCostPerMillion);
            profile.setOutputCostPerMillion(outputCostPerMillion);
            profile.setContextWindow(contextWindow);
            profile.setMaxOutputTokens(maxOutputTokens);
            profile.setSupportsStreaming(Boolean.TRUE.equals(supportsStreaming));
            profile.setSupportsToolCalling(Boolean.TRUE.equals(supportsToolCalling));
            profile.setSupportsJsonSchema(Boolean.TRUE.equals(supportsJsonSchema));
            if (latencyClass != null) {
                profile.setLatencyClass(latencyClass);
            }
            profile.setLocalOnly(Boolean.TRUE.equals(localOnly));
            profile.setEnabled(enabled == null || enabled);
            profile.setMetadata(metadata);
            return profile;
        }
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