package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import com.fluxcore.approval.dto.ApprovalHistoryRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalActionMapper extends BaseMapper<ApprovalActionEntity> {
    @Select("SELECT * FROM approval_action WHERE approval_instance_id=#{approvalInstanceId} AND action_request_id=#{actionRequestId} LIMIT 1")
    ApprovalActionEntity selectByActionRequestId(@Param("approvalInstanceId") long approvalInstanceId,
                                                 @Param("actionRequestId") String actionRequestId);

    @Select("SELECT a.id AS action_id, a.node_instance_id, a.task_id, n.node_name, a.operator_id, "
            + "a.action_type, a.from_status, a.to_status, a.comment, a.action_request_id, a.snapshot_id, "
            + "s.snapshot_no, s.snapshot_type, s.data_json AS snapshot_data_json, "
            + "s.data_hash AS snapshot_data_hash, a.created_at AS action_created_at, "
            + "s.created_at AS snapshot_created_at "
            + "FROM approval_action a "
            + "LEFT JOIN approval_node_instance ni ON ni.id=a.node_instance_id "
            + "LEFT JOIN approval_node n ON n.id=ni.node_id "
            + "LEFT JOIN approval_snapshot s ON s.id=a.snapshot_id "
            + "WHERE a.approval_instance_id=#{approvalInstanceId} "
            + "ORDER BY a.created_at ASC, a.id ASC")
    List<ApprovalHistoryRecord> selectHistoryByInstanceId(@Param("approvalInstanceId") long approvalInstanceId);
}
