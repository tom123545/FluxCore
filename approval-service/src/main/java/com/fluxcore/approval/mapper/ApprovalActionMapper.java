package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalActionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalActionMapper extends BaseMapper<ApprovalActionEntity> {
    @Select("SELECT * FROM approval_action WHERE approval_instance_id=#{approvalInstanceId} AND action_request_id=#{actionRequestId} LIMIT 1")
    ApprovalActionEntity selectByActionRequestId(@Param("approvalInstanceId") long approvalInstanceId,
                                                 @Param("actionRequestId") String actionRequestId);
}
