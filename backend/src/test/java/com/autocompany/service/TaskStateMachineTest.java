package com.autocompany.service;

import com.autocompany.entity.enums.TaskStatus;
import org.junit.jupiter.api.Test;

import static com.autocompany.entity.enums.TaskStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TASK-001 — §7.1 상태 전이도 검증 (허용 간선·금지 간선·터미널 상태). */
public class TaskStateMachineTest {

    @Test
    public void allowsDocumentedTransitions() {
        assertTrue(TaskStateMachine.canTransition(DRAFT, READY));
        assertTrue(TaskStateMachine.canTransition(READY, ASSIGNED));
        assertTrue(TaskStateMachine.canTransition(READY, BLOCKED));
        assertTrue(TaskStateMachine.canTransition(ASSIGNED, IN_PROGRESS));

        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, REVIEW));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, WAITING_FOR_QUOTA));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, BLOCKED));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, READY));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, IN_PROGRESS));
        assertTrue(TaskStateMachine.canTransition(IN_PROGRESS, FAILED));

        assertTrue(TaskStateMachine.canTransition(REVIEW, DONE));
        assertTrue(TaskStateMachine.canTransition(REVIEW, IN_PROGRESS));
        assertTrue(TaskStateMachine.canTransition(REVIEW, BLOCKED));

        assertTrue(TaskStateMachine.canTransition(WAITING_FOR_QUOTA, ASSIGNED));
        assertTrue(TaskStateMachine.canTransition(WAITING_FOR_QUOTA, CANCELLED));

        assertTrue(TaskStateMachine.canTransition(BLOCKED, READY));
        assertTrue(TaskStateMachine.canTransition(BLOCKED, CANCELLED));
    }

    @Test
    public void rejectsUndocumentedTransitions() {
        assertFalse(TaskStateMachine.canTransition(DRAFT, ASSIGNED));
        assertFalse(TaskStateMachine.canTransition(DRAFT, IN_PROGRESS));
        assertFalse(TaskStateMachine.canTransition(DRAFT, DONE));
        assertFalse(TaskStateMachine.canTransition(READY, IN_PROGRESS));
        assertFalse(TaskStateMachine.canTransition(READY, DONE));
        assertFalse(TaskStateMachine.canTransition(ASSIGNED, REVIEW));
        assertFalse(TaskStateMachine.canTransition(ASSIGNED, DONE));
        assertFalse(TaskStateMachine.canTransition(REVIEW, WAITING_FOR_QUOTA));
        assertFalse(TaskStateMachine.canTransition(WAITING_FOR_QUOTA, READY));
        assertFalse(TaskStateMachine.canTransition(BLOCKED, ASSIGNED));
        assertFalse(TaskStateMachine.canTransition(null, READY));
        assertFalse(TaskStateMachine.canTransition(READY, null));
    }

    @Test
    public void terminalStatesHaveNoOutgoingTransitions() {
        for (TaskStatus terminal : new TaskStatus[]{DONE, FAILED, CANCELLED}) {
            for (TaskStatus target : TaskStatus.values()) {
                assertFalse(TaskStateMachine.canTransition(terminal, target),
                        terminal + " → " + target + " 은 금지되어야 한다");
            }
            assertTrue(TaskStateMachine.allowedFrom(terminal).isEmpty(),
                    terminal + " 은 후속 상태가 없어야 한다");
        }
    }
}
