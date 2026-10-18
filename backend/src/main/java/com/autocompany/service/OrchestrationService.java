package com.autocompany.service;

import com.autocompany.entity.*;
import com.autocompany.entity.enums.Role;
import com.autocompany.entity.enums.Status;
import com.autocompany.entity.enums.TaskStatus;
import com.autocompany.entity.enums.TaskType;
import com.autocompany.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 자율 오케스트레이션 (ORCH-001/002, §29) — 동결 RequirementSet 기반 결정적 Mock 분해·배정·재계획.
 * 분해·배정 결정 근거는 OrchestratorCycle(§5.27)로 기록하고, 동일 세트 재트리거는 409로 거부한다.
 */
@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final RequirementSetRepository requirementSetRepository;
    private final RequirementSetService requirementSetService;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final OrchestratorCycleRepository cycleRepository;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    @Value("${orchestrator.replan_attempts_per_requirement_set:2}")
    private int replanAttemptsPerRequirementSet = 2;

    public record CycleView(UUID id, UUID requirementSetId, int requirementSetVersion, String mode,
                            int createdTaskCount, int blockedTaskCount, LocalDateTime createdAt) {
    }

    public record TaskView(UUID id, UUID parentTaskId, String type, String title, String status,
                           UUID assigneeId, String assigneeName, String blockedReason) {
    }

    public record LatestFrozen(UUID requirementSetId, int version, boolean decomposed) {
    }

    public record OrchestrationView(CycleView cycle, List<TaskView> tasks, LatestFrozen latestFrozen) {
    }

    // ==================== 트리거: 분해·배정 (§29.2) ====================

    @Transactional
    public CycleView trigger(UUID requirementSetId, UUID userId) {
        RequirementSet set = requirementSetRepository.findById(requirementSetId)
                .orElseThrow(() -> new NoSuchElementException("Requirement set not found: " + requirementSetId));
        if (set.getStatus() != RequirementSet.SetStatus.FROZEN) {
            throw new IllegalStateException("NOT_FROZEN: 동결된 요구사항 세트만 분해할 수 있습니다(§29.1)");
        }
        if (set.getDerivedTaskIds() != null && !set.getDerivedTaskIds().isEmpty()) {
            throw new IllegalStateException("ALREADY_DECOMPOSED: 이미 분해된 요구 세트입니다(§5.27)");
        }

        Project project = set.getProject();
        UUID projectId = project.getId();
        boolean firstCycle = cycleRepository.findByProjectIdOrderByCreatedAtDesc(projectId).isEmpty();
        OrchestratorCycle.CycleMode mode = firstCycle ? OrchestratorCycle.CycleMode.FIRST : OrchestratorCycle.CycleMode.REPLAN;
        if (mode == OrchestratorCycle.CycleMode.REPLAN
                && cycleRepository.countByProjectIdAndMode(projectId, OrchestratorCycle.CycleMode.REPLAN) >= replanAttemptsPerRequirementSet) {
            throw new IllegalStateException("REPLAN_LIMIT: 재계획 한도(" + replanAttemptsPerRequirementSet + "회)를 초과했습니다(§23)");
        }

        RequirementSetService.RequirementContent content = requirementSetService.parseContent(set);
        UUID plannerId = findPlannerId(project);
        String versionLabel = "v" + set.getVersion();

        List<Task> created = new ArrayList<>();
        List<Map<String, Object>> assignmentLog = new ArrayList<>();

        Task epic = newTask(project, null, TaskType.EPIC, versionLabel + " 요구사항 구현",
                "동결 요구사항 " + versionLabel + " 기반 자동 분해(Mock §5.27)",
                toJson(Map.of(
                        "requirementSetId", set.getId().toString(),
                        "version", set.getVersion(),
                        "constraints", itemMaps(content.constraints()))),
                userId, plannerId);
        created.add(epic);

        int blockedCount = 0;
        for (RequirementSetService.RequirementItem item : orEmpty(content.functional())) {
            String summary = summarize(item);
            Task story = newTask(project, epic, TaskType.STORY, summary,
                    itemDetail(item), toJson(itemMap(item)), userId, plannerId);
            Task implementation = newTask(project, story, TaskType.TASK, "구현 — " + summary,
                    itemDetail(item), toJson(itemMap(item)), userId, plannerId);
            Task verification = newTask(project, story, TaskType.TEST, "검증 — " + summary,
                    itemDetail(item), toJson(itemMap(item)), userId, plannerId);
            taskService.addDependency(verification.getId(), implementation.getId(), TaskDependency.DependencyType.BLOCKS);
            blockedCount += assign(implementation, userId, plannerId, assignmentLog) ? 1 : 0;
            blockedCount += assign(verification, userId, plannerId, assignmentLog) ? 1 : 0;
            created.add(story);
            created.add(implementation);
            created.add(verification);
        }

        for (RequirementSetService.RequirementItem item : orEmpty(content.nonFunctional())) {
            Task task = newTask(project, epic, TaskType.TASK, "비기능 — " + summarize(item),
                    itemDetail(item), toJson(itemMap(item)), userId, plannerId);
            blockedCount += assign(task, userId, plannerId, assignmentLog) ? 1 : 0;
            created.add(task);
        }

        set.setDerivedTaskIds(created.stream().map(Task::getId).toList());
        requirementSetRepository.save(set);

        Map<String, Object> decisions = new LinkedHashMap<>();
        decisions.put("version", set.getVersion());
        decisions.put("mode", mode.name());
        decisions.put("functionalItems", orEmpty(content.functional()).size());
        decisions.put("nonFunctionalItems", orEmpty(content.nonFunctional()).size());
        decisions.put("outOfScopeSkipped", orEmpty(content.outOfScope()).size());
        decisions.put("openRisksSkipped", orEmpty(content.openRisks()).size());
        decisions.put("assignments", assignmentLog);
        decisions.put("engine", "mock-deterministic");

        OrchestratorCycle cycle = cycleRepository.save(OrchestratorCycle.builder()
                .projectId(projectId)
                .requirementSetId(set.getId())
                .mode(mode)
                .createdTaskCount(created.size())
                .blockedTaskCount(blockedCount)
                .decisionsJson(toJson(decisions))
                .createdByUserId(userId)
                .build());
        return toCycleView(cycle, set.getVersion());
    }

    // ==================== 조회 (§29.4) ====================

    @Transactional(readOnly = true)
    public OrchestrationView summary(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
        List<OrchestratorCycle> cycles = cycleRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        CycleView cycleView = cycles.isEmpty() ? null : toCycleView(cycles.get(0), requirementSetVersion(cycles.get(0).getRequirementSetId()));
        List<TaskView> tasks = taskRepository.findByProjectId(projectId).stream().map(this::toTaskView).toList();
        LatestFrozen latestFrozen = requirementSetRepository.findByProjectIdOrderByVersionDesc(projectId).stream()
                .filter(s -> s.getStatus() == RequirementSet.SetStatus.FROZEN)
                .findFirst()
                .map(s -> new LatestFrozen(s.getId(), s.getVersion(),
                        s.getDerivedTaskIds() != null && !s.getDerivedTaskIds().isEmpty()))
                .orElse(null);
        return new OrchestrationView(cycleView, tasks, latestFrozen);
    }

    // ==================== 배정 (§29.3 Mock) ====================

    /** task_types가 TaskType을 포함하는 active 직급 → 해당 직급 직원(미완료 최소·이름 순). 실패 시 BLOCKED. */
    private boolean assign(Task task, UUID userId, UUID plannerId, List<Map<String, Object>> assignmentLog) {
        if (task.getType() == TaskType.EPIC || task.getType() == TaskType.STORY) {
            return false; // Epic/Story는 배정하지 않는다(§5.27)
        }
        Optional<Employee> pick = pickAssignee(task.getType());
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("taskId", task.getId().toString());
        log.put("type", task.getType().name());
        if (pick.isEmpty()) {
            taskService.transition(task.getId(), TaskStatus.BLOCKED,
                    "배정 가능한 직원 없음 — " + task.getType() + " 역량 직급/직원 필요(§29.3)",
                    userId, plannerId);
            log.put("blocked", true);
            assignmentLog.add(log);
            return true;
        }
        Employee assignee = pick.get();
        task.setAssignee(assignee);
        taskRepository.save(task);
        taskService.transition(task.getId(), TaskStatus.ASSIGNED,
                "task_types 자동 배정 — " + assignee.getName() + "(§29.3)", userId, plannerId);
        log.put("blocked", false);
        log.put("assigneeId", assignee.getId().toString());
        log.put("assigneeName", assignee.getName());
        assignmentLog.add(log);
        return false;
    }

    private Optional<Employee> pickAssignee(TaskType type) {
        List<UUID> gradeIds = jobGradeRepository.findByStatus(JobGrade.Status.ACTIVE).stream()
                .filter(g -> g.getTaskTypes() != null && g.getTaskTypes().contains(type.name()))
                .map(JobGrade::getId)
                .toList();
        if (gradeIds.isEmpty()) {
            return Optional.empty();
        }
        return employeeRepository.findAll().stream()
                .filter(e -> e.getGradeId() != null && gradeIds.contains(e.getGradeId()))
                .filter(e -> e.getStatus() != Status.OFFLINE)
                .sorted(Comparator
                        .comparingLong((Employee e) -> openTaskCount(e.getId()))
                        .thenComparing(Employee::getName, Comparator.nullsLast(String::compareTo)))
                .findFirst();
    }

    private long openTaskCount(UUID employeeId) {
        return taskRepository.findByAssigneeId(employeeId).stream()
                .filter(t -> t.getStatus() == TaskStatus.ASSIGNED
                        || t.getStatus() == TaskStatus.IN_PROGRESS
                        || t.getStatus() == TaskStatus.REVIEW)
                .count();
    }

    // ==================== 내부 ====================

    private Task newTask(Project project, Task parent, TaskType type, String title, String detail,
                         String acceptanceCriteria, UUID userId, UUID plannerId) {
        return taskRepository.save(Task.builder()
                .project(project)
                .parentTask(parent)
                .type(type)
                .title(title)
                .description(detail)
                .acceptanceCriteria(acceptanceCriteria)
                .status(TaskStatus.READY)
                .createdByUserId(userId)
                .createdByEmployeeId(plannerId)
                .build());
    }

    private UUID findPlannerId(Project project) {
        return employeeRepository.findByUserId(project.getUser().getId()).stream()
                .filter(e -> e.getRole() == Role.PLANNER)
                .min(Comparator.comparing(Employee::getName, Comparator.nullsLast(String::compareTo)))
                .map(Employee::getId)
                .orElse(null);
    }

    private String summarize(RequirementSetService.RequirementItem item) {
        String base = item.title() == null ? "" : item.title();
        if (item.detail() != null && !item.detail().isBlank()) {
            base = base.isBlank() ? item.detail() : base + " — " + item.detail();
        }
        return base.length() > 140 ? base.substring(0, 140) + "…" : base;
    }

    private String itemDetail(RequirementSetService.RequirementItem item) {
        return item.detail();
    }

    private Map<String, Object> itemMap(RequirementSetService.RequirementItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("title", item.title());
        map.put("detail", item.detail());
        map.put("source", item.source());
        map.put("evidence", item.evidence());
        if (item.questionId() != null) {
            map.put("questionId", item.questionId().toString());
        }
        return map;
    }

    private List<Map<String, Object>> itemMaps(List<RequirementSetService.RequirementItem> items) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (RequirementSetService.RequirementItem item : orEmpty(items)) {
            maps.add(itemMap(item));
        }
        return maps;
    }

    private static List<RequirementSetService.RequirementItem> orEmpty(List<RequirementSetService.RequirementItem> list) {
        return list == null ? List.of() : list;
    }

    private CycleView toCycleView(OrchestratorCycle cycle, int version) {
        return new CycleView(cycle.getId(), cycle.getRequirementSetId(), version, cycle.getMode().name(),
                cycle.getCreatedTaskCount(), cycle.getBlockedTaskCount(), cycle.getCreatedAt());
    }

    private int requirementSetVersion(UUID requirementSetId) {
        return requirementSetRepository.findById(requirementSetId).map(RequirementSet::getVersion).orElse(0);
    }

    private TaskView toTaskView(Task task) {
        return new TaskView(task.getId(),
                task.getParentTask() == null ? null : task.getParentTask().getId(),
                task.getType() == null ? null : task.getType().name(),
                task.getTitle(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getAssignee() == null ? null : task.getAssignee().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getName(),
                task.getBlockedReason());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Orchestration JSON serialize failed", e);
        }
    }
}
