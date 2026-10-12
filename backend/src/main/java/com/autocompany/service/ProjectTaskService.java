package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public Project createProject(User user, String name, String description) {
        Project project = Project.builder()
                .user(user)
                .name(name)
                .description(description)
                .status("PLANNING")
                .build();
        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Optional<Project> getProject(UUID projectId) {
        return projectRepository.findById(projectId);
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByUser(UUID userId) {
        return projectRepository.findByUserId(userId);
    }

    @Transactional
    public void addEmployeeToProject(UUID projectId, UUID employeeId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.getAssignedEmployeeIds().add(employeeId);
        projectRepository.save(project);
    }

    @Transactional
    public void removeEmployeeFromProject(UUID projectId, UUID employeeId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        project.getAssignedEmployeeIds().remove(employeeId);
        projectRepository.save(project);
    }
}

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final PeerRatingRepository ratingRepository;
    private final DiscussionMessageRepository messageRepository;
    private final CostPerformanceRouterService routerService;
    private final DockerManagerService dockerManagerService;
    private final RatingEngineService ratingEngineService;

    @Transactional
    public Task assignTask(UUID projectId, UUID assigneeId, TaskType type, String title, String prompt) {
        Employee assignee = employeeRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        Task task = Task.builder()
                .project(Project.builder().id(projectId).build())
                .assignee(assignee)
                .type(type)
                .status(TaskStatus.PENDING)
                .title(title)
                .prompt(prompt)
                .estimatedCost(0.0f)
                .totalCost(0.0f)
                .escalationCount(0)
                .success(false)
                .build();

        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public Optional<Task> getTask(UUID taskId) {
        return taskRepository.findById(taskId);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByAssignee(UUID assigneeId) {
        return taskRepository.findByAssigneeId(assigneeId);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByProject(UUID projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    @Transactional
    public Task executeTask(UUID taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        
        // 1. Cost-Performance Router로 모델 선택
        CostPerformanceRouterService.TaskClassification classification = 
                routerService.classifyTask(task);
        String selectedModel = routerService.selectBestModel(classification);
        
        if (selectedModel == null) {
            task.setStatus(TaskStatus.FAILED);
            task.setDockerExecutionResult("No model meets quality requirement");
            return taskRepository.save(task);
        }

        // 2. 직원 모델 업데이트 및 상태 변경
        Employee employee = task.getAssignee();
        employee.setAiModel(selectedModel);
        employee.setStatus(Employee.Status.WORKING);
        employeeRepository.save(employee);

        // 3. Docker 격리 환경에서 실행
        ExecutionResult result = dockerManagerService.executeInContainer(
                employee, task, classification, selectedModel
        );

        // 4. 결과 기록
        task.setAiOutput(result.output);
        task.setDockerExecutionResult(result.logs);
        task.setVerificationResults(result.verification);
        task.setEstimatedCost(result.estimatedCost);
        task.setTotalCost(task.getTotalCost() + result.estimatedCost);
        task.setSuccess(result.success);

        // 5. 실험 기록 (학습용)
        routerService.logExperiment(
                selectedModel, task.getType().name().toLowerCase(),
                result.success, result.estimatedCost, result.verificationPassed
        );

        if (result.success) {
            task.setStatus(TaskStatus.COMPLETED);
            employee.setCompletedTasks(employee.getCompletedTasks() + 1);
        } else {
            task.setStatus(TaskStatus.FAILED);
            
            // 6. Escalation 시도
            String escalationModel = routerService.getEscalationModel(
                    selectedModel, task.getType().name().toLowerCase()
            );
            
            if (escalationModel != null) {
                task.setStatus(TaskStatus.ESCALATED);
                task.setEscalationCount(task.getEscalationCount() + 1);
                // 재시도 로직은 비동기로 처리 권장
            }
        }

        employee.setStatus(Employee.Status.IDLE);
        employee.setTotalTasks(employee.getTotalTasks() + 1);
        employeeRepository.save(employee);
        
        return taskRepository.save(task);
    }

    @Transactional
    public PeerRating submitRating(Task task, UUID raterId, UUID ratedId,
                                   String category, float score, String comment) {
        PeerRating rating = PeerRating.builder()
                .task(task)
                .raterId(raterId)
                .ratedId(ratedId)
                .category(Category.valueOf(category.toUpperCase()))
                .score(score)
                .comment(comment)
                .build();
        
        PeerRating saved = ratingRepository.save(rating);
        
        // Rating Engine으로 레벨 업데이트
        ratingEngineService.updateRating(ratedId, score);
        
        return saved;
    }

    @Transactional
    public DiscussionMessage sendMessage(Project project, UUID senderId, UUID targetId,
                                         String content, String messageType) {
        DiscussionMessage message = DiscussionMessage.builder()
                .project(project)
                .senderEmployeeId(senderId)
                .targetEmployeeId(targetId)
                .content(content)
                .messageType(MessageType.valueOf(messageType.toUpperCase()))
                .build();
        return messageRepository.save(message);
    }

    // DTO for execution result
    public static class ExecutionResult {
        public String output;
        public String logs;
        public String verification;
        public float estimatedCost;
        public boolean success;
        public boolean verificationPassed;
    }
}