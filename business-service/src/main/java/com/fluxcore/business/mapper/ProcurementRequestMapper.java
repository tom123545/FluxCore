package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ProcurementRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProcurementRequestMapper extends BaseMapper<ProcurementRequestEntity> {
    @Select("SELECT * FROM procurement_request WHERE application_id = #{applicationId} LIMIT 1")
    ProcurementRequestEntity selectByApplicationId(@Param("applicationId") long applicationId);
}
