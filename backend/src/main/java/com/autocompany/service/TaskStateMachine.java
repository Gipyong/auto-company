package com.autocompany.service;

import com.autocompany.entity.enums.TaskStatus;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Task 상태 머신 - DESIGN.md Section 7.1 구현
 * 
 * 유효한 상태 전이:
 * DRAFT → READY (요구사항/인수 조건 확정)
 * READY → ASSIGNED (직원 및 예산/권한 확인)
 * ASSIGNED → IN_PROGRESS (workspace 준비)
 * IN_PROGRESS → REVIEW (작업 완료)
 * IN_PROGRESS → WAITING_FOR_QUOTA (쿼터/한도)
 * IN_PROGRESS → BLOCKED (환경/권한/외부 의존성)
 * IN_PROGRESS → READY (재시도 가능 오류)
 * IN_PROGRESS → FAILED (최대 시도 초과)
 * REVIEW → DONE (승인 + 품질 게이트 통과)
 * REVIEW → IN_PROGRESS (수정 요청)
 * REVIEW → BLOCKED (검증 불가능)
 * WAITING_FOR_QUOTA → ASSIGNED (쿼터 복구/허용 대체 모델)
 * WAITING_FOR_QUOTA → CANCELLED (CEO 취소)
 * BLOCKED → READY (차단 사유 해결)
 * BLOCKED → CANCELLED (포기)
 */
@Service
public class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> VALID_TRANSITIONS = Map.ofEntries(
        // DRAFT: 요구사항 확정 후 READY로
        Map.entry(TaskStatus.DRAFT, Set.of(TaskStatus.READY, TaskStatus.CANCELLED)),
        
        // READY: 직원/예산/권한 확인 후 ASSIGNED로
        Map.entry(TaskStatus.READY, Set.of(TaskStatus.ASSIGNED, TaskStatus.CANCELLED)),
        
        // ASSIGNED: workspace 준비 후 IN_PROGRESS로
        Map.entry(TaskStatus.ASSIGNED, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED)),
        
        // IN_PROGRESS: 다양한 결과로 전이 가능
        Map.entry(TaskStatus.IN_PROGRESS, Set.of(
            TaskStatus.REVIEW, 
            TaskStatus.WAITING_FOR_QUOTA, 
            TaskStatus.BLOCKED, 
            TaskStatus.READY,     // 재시도
            TaskStatus.FAILED,    // 최대 시도 초과
            TaskStatus.CANCELLED
        )),
        
        // REVIEW: 승인 시 DONE, 수정 요청 시 IN_PROGRESS, 검증 불가 시 BLOCKED
        Map.entry(TaskStatus.REVIEW, Set.of(
            TaskStatus.DONE, 
            TaskStatus.IN_PROGRESS, 
            TaskStatus.BLOCKED,
            TaskStatus.CANCELLED
        )),
        
        // WAITING_FOR_QUOTA: 쿼터 복구 시 ASSIGNED, 취소 시 CANCELLED
        Map.entry(TaskStatus.WAITING_FOR_QUOTA, Set.of(
            TaskStatus.ASSIGNED, 
            TaskStatus.CANCELLED
        )),
        
        // BLOCKED: 차단 해결 시 READY, 포기 시 CANCELLED
        Map.entry(TaskStatus.BLOCKED, Set.of(
            TaskStatus.READY, 
            TaskStatus.CANCELLED
        )),
        
        // 종료 상태: 전이 불가
        Map.entry(TaskStatus.DONE, Set.of()),
        Map.entry(TaskStatus.FAILED, Set.of()),
        Map.entry(TaskStatus.CANCELLED, Set.of())
    );

    /**
     * 상태 전이 유효성 검증
     */
    public boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == to) {
            return true; // 같은 상태는 허용 (idempotent)
        }
        Set<TaskStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        return allowed.contains(to);
    }

    /**
     * 상태 전이 실행 (검증 포함)
     */
    public void transition(TaskStatus from, TaskStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                String.format("Invalid task status transition: %s → %s. Allowed: %s", 
                    from, to, VALID_TRANSITIONS.getOrDefault(from, Set.of()))
            );
        }
    }

    /**
     * 현재 상태에서 가능한 다음 상태들 반환
     */
    public Set<TaskStatus> getAllowedTransitions(TaskStatus from) {
        return Set.copyOf(VALID_TRANSITIONS.getOrDefault(from, Set.of()));
    }

    /**
     * 종료 상태인지 확인
     */
    public boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.DONE 
            || status == TaskStatus.FAILED 
            || status == TaskStatus.CANCELLED;
    }

    /**
     * 활성 상태인지 확인 (작업 중인 상태)
     */
    public boolean isActive(TaskStatus status) {
        return status == TaskStatus.IN_PROGRESS 
            || status == TaskStatus.REVIEW 
            || status == TaskStatus.WAITING_FOR_QUOTA 
            || status == TaskStatus.BLOCKED;
    }

    /**
     * 할당 가능한 상태인지 확인 (READY, ASSIGNED)
     */
    public boolean isAssignable(TaskStatus status) {
        return status == TaskStatus.READY || status == TaskStatus.ASSIGNED;
    }
}