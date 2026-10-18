package com.autocompany.service;

import com.autocompany.entity.enums.TaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * §7.1 Task 상태 전이 (TASK-001) — 표에 정의된 전이만 허용한다.
 * 무효 전이는 서비스/API에서 거부되고, 모든 전이는 task_transitions에 기록된다.
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED.put(TaskStatus.DRAFT, EnumSet.of(TaskStatus.READY));
        ALLOWED.put(TaskStatus.READY, EnumSet.of(TaskStatus.ASSIGNED, TaskStatus.BLOCKED));
        ALLOWED.put(TaskStatus.ASSIGNED, EnumSet.of(TaskStatus.IN_PROGRESS));
        ALLOWED.put(TaskStatus.IN_PROGRESS, EnumSet.of(
                TaskStatus.REVIEW, TaskStatus.WAITING_FOR_QUOTA, TaskStatus.BLOCKED,
                TaskStatus.READY, TaskStatus.IN_PROGRESS, TaskStatus.FAILED));
        ALLOWED.put(TaskStatus.REVIEW, EnumSet.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED));
        ALLOWED.put(TaskStatus.WAITING_FOR_QUOTA, EnumSet.of(TaskStatus.ASSIGNED, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.BLOCKED, EnumSet.of(TaskStatus.READY, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.FAILED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStateMachine() {
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to);
    }

    public static Set<TaskStatus> allowedFrom(TaskStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TaskStatus.class));
    }
}
