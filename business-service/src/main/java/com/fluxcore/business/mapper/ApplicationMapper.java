package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ApplicationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApplicationMapper extends BaseMapper<ApplicationEntity> {
    @Select("SELECT * FROM application WHERE business_type = #{businessType} AND business_id = #{businessId} LIMIT 1")
    ApplicationEntity selectByBusiness(@Param("businessType") String businessType,
                                       @Param("businessId") String businessId);

    @Select("SELECT * FROM application WHERE business_type = #{businessType} AND idempotency_key = #{idempotencyKey} LIMIT 1")
    ApplicationEntity selectByIdempotencyKey(@Param("businessType") String businessType,
                                             @Param("idempotencyKey") String idempotencyKey);

    @Update("UPDATE application SET status = 'SUBMITTED', submitted_at = NOW(3), updated_at = NOW(3), version = version + 1 WHERE id = #{applicationId} AND status = 'DRAFT'")
    int markSubmitted(@Param("applicationId") long applicationId);
}
