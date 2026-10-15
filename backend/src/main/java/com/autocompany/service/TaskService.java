package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.*;
import com.autocompany.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.UUID;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final PeerRatingRepository ratingRepository;
    private final DiscussionMessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final CostPerformanceRouterService routerService;
    private final DockerManagerService dockerManagerService;
    private final RatingEngineService ratingEngineService;

    @Transactional
    public Task assignTask(UUID projectId, UUID assigneeId, TaskType taskType,
                           String title, String description) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        Employee assignee = employeeRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + assigneeId));

        Task task = Task.builder()
                .project(project)
                .assignee(assignee)
                .type(taskType)
                .title(title)
                .description(description)
                .status(TaskStatus.DRAFT)
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
    public PeerRating submitRating(Task task, UUID raterId, UUID ratedId,
                                   String category, float score, String comment) {
        PeerRating rating = PeerRating.builder()
                .task(task)
                .raterEmployeeId(raterId)
                .ratedEmployeeId(ratedId)
                .category(Category.valueOf(category.toUpperCase()))
                .score(score)
                .comment(comment)
                .build();
        return ratingRepository.save(rating);
    }

    @Transactional
    public DiscussionMessage sendMessage(Project project, UUID senderId, UUID targetId,
                                           String content, String messageType) {
        DiscussionMessage msg = DiscussionMessage.builder()
                .project(project)
                .senderEmployeeId(senderId)
                .targetEmployeeId(targetId)
                .content(content)
                .messageType(MessageType.valueOf(messageType.toUpperCase()))
                .build();
        return messageRepository.save(msg);
    }

    @Transactional
    public Task updateTaskStatus(UUID taskId, TaskStatus newStatus) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        task.setStatus(newStatus);
        return taskRepository.save(task);
    }
}
