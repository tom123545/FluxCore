package com.fluxcore.approval.state;

/**
 * 审批实例状态。
 *
 * <p>实例状态描述整张审批单的生命周期，不描述当前处于第几级审批。
 * 当前节点由 approval_instance.current_node_id 表示。</p>
 */
public enum ApprovalInstanceStatus {
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
