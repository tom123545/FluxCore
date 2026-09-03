package com.fluxcore.approval.state;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 串行审批状态机。
 *
 * <p>状态机只负责定义和校验领域状态转换；真正的并发安全由业务事务、
 * Redis 实例锁、数据库条件更新和审批实例 lock_version 共同保证。</p>
 */
@Component
public class ApprovalStateMachine {
    private final Map<ApprovalInstanceStatus, Set<ApprovalInstanceStatus>> instanceTransitions =
            new EnumMap<>(ApprovalInstanceStatus.class);
    private final Map<ApprovalTaskStatus, Set<ApprovalTaskStatus>> taskTransitions =
            new EnumMap<>(ApprovalTaskStatus.class);
    private final Map<ApprovalNodeInstanceStatus, Set<ApprovalNodeInstanceStatus>> nodeTransitions =
            new EnumMap<>(ApprovalNodeInstanceStatus.class);

    public ApprovalStateMachine() {
        instanceTransitions.put(ApprovalInstanceStatus.IN_PROGRESS,
                EnumSet.of(ApprovalInstanceStatus.APPROVED,
                        ApprovalInstanceStatus.REJECTED,
                        ApprovalInstanceStatus.WITHDRAWN));

        taskTransitions.put(ApprovalTaskStatus.PENDING,
                EnumSet.of(ApprovalTaskStatus.APPROVED,
                        ApprovalTaskStatus.REJECTED,
                        ApprovalTaskStatus.CANCELLED,
                        ApprovalTaskStatus.TRANSFERRED));

        nodeTransitions.put(ApprovalNodeInstanceStatus.ACTIVE,
                EnumSet.of(ApprovalNodeInstanceStatus.COMPLETED,
                        ApprovalNodeInstanceStatus.REJECTED,
                        ApprovalNodeInstanceStatus.CANCELLED));
    }

    public boolean canTransitionInstance(String from, String to) {
        return canTransition(from, to, ApprovalInstanceStatus.class, instanceTransitions);
    }

    public boolean canTransitionTask(String from, String to) {
        return canTransition(from, to, ApprovalTaskStatus.class, taskTransitions);
    }

    public boolean canTransitionNode(String from, String to) {
        return canTransition(from, to, ApprovalNodeInstanceStatus.class, nodeTransitions);
    }

    public boolean isInstanceActionable(String status) {
        return statusEquals(status, ApprovalInstanceStatus.IN_PROGRESS);
    }

    public boolean isTaskActionable(String status) {
        return statusEquals(status, ApprovalTaskStatus.PENDING);
    }

    public boolean isNodeActive(String status) {
        return statusEquals(status, ApprovalNodeInstanceStatus.ACTIVE);
    }

    private <T extends Enum<T>> boolean canTransition(String from, String to, Class<T> type,
                                                       Map<T, Set<T>> transitions) {
        if (from == null || to == null) {
            return false;
        }
        try {
            T source = Enum.valueOf(type, from.trim().toUpperCase(Locale.ROOT));
            T target = Enum.valueOf(type, to.trim().toUpperCase(Locale.ROOT));
            return transitions.getOrDefault(source, Set.of()).contains(target);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean statusEquals(String actual, Enum<?> expected) {
        return actual != null && expected.name().equalsIgnoreCase(actual.trim());
    }
}
