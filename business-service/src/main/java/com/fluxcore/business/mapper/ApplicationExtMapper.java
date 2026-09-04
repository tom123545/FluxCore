package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ApplicationExtEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplicationExtMapper extends BaseMapper<ApplicationExtEntity> {
    @Select("SELECT * FROM application_ext WHERE application_id = #{applicationId} LIMIT 1")
    ApplicationExtEntity selectByApplicationId(@Param("applicationId") long applicationId);
}
