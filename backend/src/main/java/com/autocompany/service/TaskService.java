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
    private final TaskTransitionRepository transitionRepository;
    private final TaskDependencyRepository dependencyRepository;
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
        task = taskRepository.save(task);
        // TASK-001 — 수동 경로도 상태 머신을 통과한다(§7.1): DRAFT → READY → ASSIGNED
        transition(task.getId(), TaskStatus.READY, "수동 생성 — 요구·인수 확정(§7.1)", null, null);
        return transition(task.getId(), TaskStatus.ASSIGNED, "수동 배정 — 담당 지정(§29.3)", null, null);
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
                                   String category, float score, String comment, List<String> evidenceRefs) {
        // Ratings now go through RatingEngineService for feedback context management
        return ratingEngineService.submitFeedback(PeerRating.builder()
                .task(task)
                .raterEmployeeId(raterId)
                .ratedEmployeeId(ratedId)
                .category(Category.valueOf(category.toUpperCase()))
                .score(score)
                .comment(comment)
                .evidenceRefs(evidenceRefs)
                .build());
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

    /** TASK-001 — §7.1 표에 없는 전이는 거부하고, 모든 전이를 task_transitions에 기록한다. */
    @Transactional
    public Task transition(UUID taskId, TaskStatus toStatus, String reason,
                           UUID triggeredByUserId, UUID triggeredByEmployeeId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        TaskStatus from = task.getStatus();
        if (!TaskStateMachine.canTransition(from, toStatus)) {
            throw new IllegalStateException("Invalid task transition: " + from + " → " + toStatus
                    + " (allowed: " + TaskStateMachine.allowedFrom(from) + ")");
        }
        task.setStatus(toStatus);
        if (toStatus == TaskStatus.BLOCKED) {
            task.setBlockedReason(reason);
        } else if (from == TaskStatus.BLOCKED) {
            task.setBlockedReason(null);
        }
        taskRepository.save(task);
        transitionRepository.save(TaskTransition.builder()
                .task(task)
                .fromStatus(from)
                .toStatus(toStatus)
                .triggeredByUserId(triggeredByUserId)
                .triggeredByEmployeeId(triggeredByEmployeeId)
                .reason(reason)
                .build());
        return task;
    }

    @Transactional
    public Task updateTaskStatus(UUID taskId, TaskStatus newStatus) {
        return transition(taskId, newStatus, "status update", null, null);
    }

    /** §5.11(TASK-001) — 의존성 생성. 자기 참조·중복·직간접 순환은 거부한다. */
    @Transactional
    public TaskDependency addDependency(UUID taskId, UUID dependsOnTaskId, TaskDependency.DependencyType dependencyType) {
        if (taskId == null || dependsOnTaskId == null) {
            throw new IllegalArgumentException("taskId and dependsOnTaskId are required");
        }
        if (taskId.equals(dependsOnTaskId)) {
            throw new IllegalArgumentException("Task cannot depend on itself");
        }
        if (!taskRepository.existsById(taskId) || !taskRepository.existsById(dependsOnTaskId)) {
            UUID missing = taskRepository.existsById(taskId) ? dependsOnTaskId : taskId;
            throw new NoSuchElementException("Task not found: " + missing);
        }
        if (dependencyRepository.existsByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId)) {
            throw new IllegalStateException("Dependency already exists: " + taskId + " → " + dependsOnTaskId);
        }
        if (hasPath(dependsOnTaskId, taskId)) {
            throw new IllegalStateException("Dependency cycle rejected: " + taskId + " → " + dependsOnTaskId);
        }
        return dependencyRepository.save(TaskDependency.builder()
                .taskId(taskId)
                .dependsOnTaskId(dependsOnTaskId)
                .dependencyType(dependencyType == null ? TaskDependency.DependencyType.BLOCKS : dependencyType)
                .build());
    }

    /** dependsOn에서 기존 의존 간선을 따라가 target에 도달하면 순환이다. */
    private boolean hasPath(UUID start, UUID target) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            for (TaskDependency d : dependencyRepository.findByTaskId(current)) {
                queue.add(d.getDependsOnTaskId());
            }
        }
        return false;
    }
}
