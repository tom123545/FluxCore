package com.fluxcore.approval.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApprovalStateMachineTest {
    private final ApprovalStateMachine stateMachine = new ApprovalStateMachine();

    @Test
    void instance_shouldStayInProgressUntilTerminalState() {
        assertTrue(stateMachine.canTransitionInstance("IN_PROGRESS", "APPROVED"));
        assertTrue(stateMachine.canTransitionInstance("IN_PROGRESS", "REJECTED"));
        assertTrue(stateMachine.canTransitionInstance("IN_PROGRESS", "WITHDRAWN"));
        assertFalse(stateMachine.canTransitionInstance("APPROVED", "IN_PROGRESS"));
        assertFalse(stateMachine.canTransitionInstance("REJECTED", "APPROVED"));
    }

    @Test
    void task_shouldOnlyMoveFromPendingOnce() {
        assertTrue(stateMachine.canTransitionTask("PENDING", "APPROVED"));
        assertTrue(stateMachine.canTransitionTask("PENDING", "REJECTED"));
        assertTrue(stateMachine.canTransitionTask("PENDING", "CANCELLED"));
        assertTrue(stateMachine.canTransitionTask("PENDING", "TRANSFERRED"));
        assertFalse(stateMachine.canTransitionTask("APPROVED", "REJECTED"));
        assertFalse(stateMachine.canTransitionTask("CANCELLED", "PENDING"));
    }

    @Test
    void node_shouldOnlyCompleteFromActive() {
        assertTrue(stateMachine.canTransitionNode("ACTIVE", "COMPLETED"));
        assertTrue(stateMachine.canTransitionNode("ACTIVE", "REJECTED"));
        assertTrue(stateMachine.canTransitionNode("ACTIVE", "CANCELLED"));
        assertFalse(stateMachine.canTransitionNode("COMPLETED", "ACTIVE"));
        assertFalse(stateMachine.canTransitionNode("ACTIVE", "ACTIVE"));
    }

    @Test
    void malformedState_shouldNeverBeAccepted() {
        assertFalse(stateMachine.canTransitionInstance(null, "APPROVED"));
        assertFalse(stateMachine.canTransitionTask(" ", "APPROVED"));
        assertFalse(stateMachine.canTransitionNode("UNKNOWN", "COMPLETED"));
        assertTrue(stateMachine.isInstanceActionable(" IN_PROGRESS "));
        assertTrue(stateMachine.isTaskActionable(" pending "));
        assertFalse(stateMachine.isInstanceActionable("IN_PROGRESS_UNKNOWN"));
    }
}
