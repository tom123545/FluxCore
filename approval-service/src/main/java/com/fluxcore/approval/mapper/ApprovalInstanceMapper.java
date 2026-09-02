package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalInstanceEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstanceEntity> {
    @Select("SELECT * FROM approval_instance WHERE application_id=#{applicationId} LIMIT 1")
    ApprovalInstanceEntity selectByApplicationId(@Param("applicationId") long id);
    @Select("SELECT * FROM approval_instance WHERE application_id=#{applicationId} AND submit_request_id=#{submitRequestId} LIMIT 1")
    ApprovalInstanceEntity selectBySubmitRequestId(@Param("applicationId") long id, @Param("submitRequestId") String requestId);
    @Select("SELECT * FROM approval_instance WHERE submit_request_id=#{submitRequestId} LIMIT 1")
    ApprovalInstanceEntity selectBySubmitRequestIdOnly(@Param("submitRequestId") String requestId);
    default Optional<ApprovalInstanceEntity> findByApplicationId(long id) { return Optional.ofNullable(selectByApplicationId(id)); }
    default Optional<ApprovalInstanceEntity> findBySubmitRequestId(String requestId) { return Optional.ofNullable(selectBySubmitRequestIdOnly(requestId)); }
}
