package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalNodeInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalNodeInstanceMapper extends BaseMapper<ApprovalNodeInstanceEntity> {
    @Select("SELECT * FROM approval_node_instance WHERE approval_instance_id=#{approvalInstanceId} AND status='ACTIVE' ORDER BY id DESC LIMIT 1")
    ApprovalNodeInstanceEntity selectActiveByInstanceId(@Param("approvalInstanceId") long approvalInstanceId);

    @Update("UPDATE approval_node_instance SET status='COMPLETED', completed_at=NOW(3), updated_at=NOW(3) "
            + "WHERE id=#{nodeInstanceId} AND approval_instance_id=#{approvalInstanceId} AND status='ACTIVE'")
    int markCompleted(@Param("nodeInstanceId") long nodeInstanceId,
                      @Param("approvalInstanceId") long approvalInstanceId);

    @Update("UPDATE approval_node_instance SET status='REJECTED', completed_at=NOW(3), updated_at=NOW(3) "
            + "WHERE id=#{nodeInstanceId} AND approval_instance_id=#{approvalInstanceId} AND status='ACTIVE'")
    int markRejected(@Param("nodeInstanceId") long nodeInstanceId,
                     @Param("approvalInstanceId") long approvalInstanceId);

    @Update("UPDATE approval_node_instance SET status='CANCELLED', completed_at=NOW(3), updated_at=NOW(3) "
            + "WHERE id=#{nodeInstanceId} AND status='ACTIVE'")
    int markCancelled(@Param("nodeInstanceId") long nodeInstanceId);
}
