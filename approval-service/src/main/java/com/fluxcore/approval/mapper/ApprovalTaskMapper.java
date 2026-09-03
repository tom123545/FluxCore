package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.dto.ApprovalTaskQueryItem;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import com.fluxcore.approval.dto.ApprovalTaskView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTaskEntity> {
    @Select("SELECT t.id AS taskId, t.approval_instance_id AS approvalInstanceId, "
            + "t.node_instance_id AS nodeInstanceId, t.source_task_id AS sourceTaskId, t.assignee_id AS assigneeId, "
            + "t.status AS taskStatus, t.action, t.comment, t.created_at AS createdAt, "
            + "t.acted_at AS actedAt, t.updated_at AS updatedAt, i.approval_no AS approvalNo, "
            + "i.application_id AS applicationId, i.business_type AS businessType, "
            + "i.business_id AS businessId, JSON_UNQUOTE(JSON_EXTRACT(s.data_json, '$.title')) AS title, "
            + "i.applicant_id AS applicantId, "
            + "i.status AS approvalStatus, i.current_node_id AS currentNodeId, "
            + "n.node_name AS nodeName, s.snapshot_no AS snapshotNo, "
            + "s.snapshot_type AS snapshotType, s.data_hash AS snapshotDataHash "
            + "FROM approval_task t "
            + "JOIN approval_instance i ON i.id=t.approval_instance_id "
            + "JOIN approval_node_instance ni ON ni.id=t.node_instance_id "
            + "JOIN approval_node n ON n.id=ni.node_id "
            + "LEFT JOIN approval_snapshot s ON s.approval_instance_id=t.approval_instance_id "
            + "AND s.snapshot_no=(SELECT MAX(s2.snapshot_no) FROM approval_snapshot s2 "
            + "WHERE s2.approval_instance_id=t.approval_instance_id) "
            + "WHERE t.assignee_id=#{assigneeId} AND t.status='PENDING' "
            + "ORDER BY t.created_at DESC, t.id DESC")
    List<ApprovalTaskQueryItem> selectTodoByAssignee(@Param("assigneeId") String assigneeId);

    @Select("SELECT t.id AS taskId, t.approval_instance_id AS approvalInstanceId, "
            + "t.node_instance_id AS nodeInstanceId, t.source_task_id AS sourceTaskId, t.assignee_id AS assigneeId, "
            + "t.status AS taskStatus, t.action, t.comment, t.created_at AS createdAt, "
            + "t.acted_at AS actedAt, t.updated_at AS updatedAt, i.approval_no AS approvalNo, "
            + "i.application_id AS applicationId, i.business_type AS businessType, "
            + "i.business_id AS businessId, JSON_UNQUOTE(JSON_EXTRACT(s.data_json, '$.title')) AS title, "
            + "i.applicant_id AS applicantId, "
            + "i.status AS approvalStatus, i.current_node_id AS currentNodeId, "
            + "n.node_name AS nodeName, s.snapshot_no AS snapshotNo, "
            + "s.snapshot_type AS snapshotType, s.data_hash AS snapshotDataHash "
            + "FROM approval_task t "
            + "JOIN approval_instance i ON i.id=t.approval_instance_id "
            + "JOIN approval_node_instance ni ON ni.id=t.node_instance_id "
            + "JOIN approval_node n ON n.id=ni.node_id "
            + "LEFT JOIN approval_snapshot s ON s.approval_instance_id=t.approval_instance_id "
            + "AND s.snapshot_no=(SELECT MAX(s2.snapshot_no) FROM approval_snapshot s2 "
            + "WHERE s2.approval_instance_id=t.approval_instance_id) "
            + "WHERE t.assignee_id=#{assigneeId} AND t.status<>'PENDING' "
            + "ORDER BY COALESCE(t.acted_at, t.created_at) DESC, t.id DESC")
    List<ApprovalTaskQueryItem> selectDoneByAssignee(@Param("assigneeId") String assigneeId);

    @Select("SELECT t.id, t.node_instance_id, t.source_task_id, ni.node_id, n.node_name, t.assignee_id, t.status, t.action, t.comment, "
            + "t.created_at, t.acted_at FROM approval_task t "
            + "JOIN approval_node_instance ni ON ni.id=t.node_instance_id "
            + "JOIN approval_node n ON n.id=ni.node_id "
            + "WHERE t.approval_instance_id=#{approvalInstanceId} ORDER BY t.id ASC")
    List<ApprovalTaskView> selectViewsByApprovalInstanceId(@Param("approvalInstanceId") long approvalInstanceId);

    @Update("UPDATE approval_task SET status='CANCELLED', updated_at=NOW(3) "
            + "WHERE node_instance_id=#{nodeInstanceId} AND status='PENDING' AND id<>#{taskId}")
    int cancelOtherPendingByNodeInstanceId(@Param("nodeInstanceId") long nodeInstanceId,
                                           @Param("taskId") long taskId);

    @Update("UPDATE approval_task SET status='APPROVED', action='APPROVE', comment=#{comment}, "
            + "acted_at=NOW(3), updated_at=NOW(3) "
            + "WHERE id=#{taskId} AND approval_instance_id=#{approvalInstanceId} "
            + "AND assignee_id=#{operatorId} AND status='PENDING'")
    int updatePendingToApproved(@Param("taskId") long taskId,
                                @Param("approvalInstanceId") long approvalInstanceId,
                                @Param("operatorId") String operatorId,
                                @Param("comment") String comment);

    @Update("UPDATE approval_task SET status='REJECTED', action='REJECT', comment=#{comment}, acted_at=NOW(3), updated_at=NOW(3) "
            + "WHERE id=#{taskId} AND approval_instance_id=#{approvalInstanceId} AND status='PENDING'")
    int updatePendingToRejected(@Param("taskId") long taskId,
                                @Param("approvalInstanceId") long approvalInstanceId,
                                @Param("comment") String comment);

    @Update("UPDATE approval_task SET status='CANCELLED', updated_at=NOW(3) "
            + "WHERE approval_instance_id=#{approvalInstanceId} AND status='PENDING' AND id<>#{taskId}")
    int cancelOtherPendingByInstanceId(@Param("approvalInstanceId") long approvalInstanceId,
                                       @Param("taskId") long taskId);

    @Update("UPDATE approval_task SET status='CANCELLED', action='WITHDRAW', acted_at=NOW(3), updated_at=NOW(3) "
            + "WHERE approval_instance_id=#{approvalInstanceId} AND status='PENDING'")
    int cancelPendingByInstanceId(@Param("approvalInstanceId") long approvalInstanceId);

    @Update("UPDATE approval_task SET status='TRANSFERRED', action='TRANSFER', comment=#{comment}, "
            + "acted_at=NOW(3), updated_at=NOW(3) WHERE id=#{taskId} "
            + "AND approval_instance_id=#{approvalInstanceId} AND assignee_id=#{operatorId} AND status='PENDING'")
    int transferPendingTask(@Param("taskId") long taskId,
                            @Param("approvalInstanceId") long approvalInstanceId,
                            @Param("operatorId") String operatorId,
                            @Param("comment") String comment);

    @Select("SELECT COUNT(*) FROM approval_task WHERE node_instance_id=#{nodeInstanceId} AND status='PENDING'")
    int countPendingByNodeInstanceId(@Param("nodeInstanceId") long nodeInstanceId);

    @Select("SELECT COUNT(*) FROM approval_task WHERE node_instance_id=#{nodeInstanceId} "
            + "AND assignee_id=#{assigneeId} AND status='PENDING'")
    int countPendingByNodeAndAssignee(@Param("nodeInstanceId") long nodeInstanceId,
                                      @Param("assigneeId") String assigneeId);
}
