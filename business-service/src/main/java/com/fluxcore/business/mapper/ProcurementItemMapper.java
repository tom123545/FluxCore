package com.fluxcore.business.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.business.entity.ProcurementItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProcurementItemMapper extends BaseMapper<ProcurementItemEntity> {
    @Select("SELECT * FROM procurement_item WHERE procurement_id = #{procurementId} ORDER BY id")
    List<ProcurementItemEntity> selectByProcurementId(@Param("procurementId") long procurementId);
}
