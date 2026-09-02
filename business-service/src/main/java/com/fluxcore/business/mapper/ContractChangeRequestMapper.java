package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ContractChangeRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContractChangeRequestMapper extends BaseMapper<ContractChangeRequestEntity> {
    @Select("SELECT * FROM contract_change_request WHERE application_id = #{applicationId} LIMIT 1")
    ContractChangeRequestEntity selectByApplicationId(@Param("applicationId") long applicationId);
}
