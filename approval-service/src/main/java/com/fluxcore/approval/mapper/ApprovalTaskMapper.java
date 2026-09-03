package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTaskEntity> {
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
}
