package com.fluxcore.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstanceEntity> {
    @Select("SELECT * FROM approval_instance WHERE application_id=#{applicationId} LIMIT 1")
    ApprovalInstanceEntity selectByApplicationId(@Param("applicationId") long id);

    @Select("SELECT * FROM approval_instance WHERE application_id=#{applicationId} AND submit_request_id=#{submitRequestId} LIMIT 1")
    ApprovalInstanceEntity selectBySubmitRequestId(@Param("applicationId") long id, @Param("submitRequestId") String requestId);

    @Select("SELECT * FROM approval_instance WHERE submit_request_id=#{submitRequestId} LIMIT 1")
    ApprovalInstanceEntity selectBySubmitRequestIdOnly(@Param("submitRequestId") String requestId);

    @Update("UPDATE approval_instance SET status=#{toStatus}, current_node_id=NULL, completed_at=NOW(3), "
            + "lock_version=lock_version + 1, updated_at=NOW(3) "
            + "WHERE id=#{approvalInstanceId} AND status=#{fromStatus} AND lock_version=#{lockVersion}")
    int updateStatusWithVersion(@Param("approvalInstanceId") long approvalInstanceId,
                                @Param("fromStatus") String fromStatus,
                                @Param("toStatus") String toStatus,
                                @Param("lockVersion") long lockVersion);

    @Update("UPDATE approval_instance SET current_node_id=#{currentNodeId}, "
            + "lock_version=lock_version + 1, updated_at=NOW(3) "
            + "WHERE id=#{approvalInstanceId} AND status='IN_PROGRESS' AND lock_version=#{lockVersion}")
    int updateCurrentNodeWithVersion(@Param("approvalInstanceId") long approvalInstanceId,
                                     @Param("currentNodeId") long currentNodeId,
                                     @Param("lockVersion") long lockVersion);

    @Update("UPDATE approval_instance SET lock_version=lock_version + 1, updated_at=NOW(3) "
            + "WHERE id=#{approvalInstanceId} AND status='IN_PROGRESS' AND lock_version=#{lockVersion}")
    int touchWithVersion(@Param("approvalInstanceId") long approvalInstanceId,
                         @Param("lockVersion") long lockVersion);

    default Optional<ApprovalInstanceEntity> findByApplicationId(long id) {
        return Optional.ofNullable(selectByApplicationId(id));
    }

    default Optional<ApprovalInstanceEntity> findBySubmitRequestId(String requestId) {
        return Optional.ofNullable(selectBySubmitRequestIdOnly(requestId));
    }
}
