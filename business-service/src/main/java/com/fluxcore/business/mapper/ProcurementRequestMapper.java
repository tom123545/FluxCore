package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ProcurementRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcurementRequestMapper extends BaseMapper<ProcurementRequestEntity> {
    @Select("SELECT * FROM procurement_request WHERE application_id = #{applicationId} LIMIT 1")
    ProcurementRequestEntity selectByApplicationId(@Param("applicationId") long applicationId);

    @Update("UPDATE procurement_request SET status='SUBMITTED', updated_at=NOW(3) WHERE application_id=#{applicationId} AND status='DRAFT'")
    int markSubmitted(@Param("applicationId") long applicationId);

    @Update("UPDATE procurement_request SET status='REJECTED', updated_at=NOW(3) WHERE application_id=#{applicationId} AND status IN ('DRAFT', 'SUBMITTED')")
    int markRejected(@Param("applicationId") long applicationId);

    @Update("UPDATE procurement_request SET status='APPROVED', updated_at=NOW(3) WHERE application_id=#{applicationId} AND status='SUBMITTED'")
    int markApproved(@Param("applicationId") long applicationId);

    @Update("UPDATE procurement_request SET status='WITHDRAWN', updated_at=NOW(3) WHERE application_id=#{applicationId} AND status='SUBMITTED'")
    int markWithdrawn(@Param("applicationId") long applicationId);

}
