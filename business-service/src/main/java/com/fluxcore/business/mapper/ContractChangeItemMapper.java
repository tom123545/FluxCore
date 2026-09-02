package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ContractChangeItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ContractChangeItemMapper extends BaseMapper<ContractChangeItemEntity> {
    @Select("SELECT * FROM contract_change_item WHERE contract_change_id = #{contractChangeId} ORDER BY id")
    List<ContractChangeItemEntity> selectByContractChangeId(@Param("contractChangeId") long contractChangeId);
}
