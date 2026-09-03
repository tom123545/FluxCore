package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalSnapshotEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprovalSnapshotMapper extends BaseMapper<ApprovalSnapshotEntity> {
    @Select("SELECT COALESCE(MAX(snapshot_no), 0) FROM approval_snapshot WHERE approval_instance_id=#{approvalInstanceId}")
    int selectMaxSnapshotNo(@Param("approvalInstanceId") long approvalInstanceId);
}
