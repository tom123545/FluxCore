package com.fluxcore.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalTransitionEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalTransitionMapper extends BaseMapper<ApprovalTransitionEntity> {
    @Select("SELECT * FROM approval_transition WHERE process_id=#{processId} "
            + "AND from_node_id=#{fromNodeId} AND condition_json IS NULL "
            + "ORDER BY priority ASC, id ASC LIMIT 1")
    ApprovalTransitionEntity selectDefaultNext(@Param("processId") long processId,
                                                @Param("fromNodeId") long fromNodeId);

    default Optional<ApprovalTransitionEntity> findDefaultNext(long processId, long fromNodeId) {
        return Optional.ofNullable(selectDefaultNext(processId, fromNodeId));
    }
}
