package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalNodeEntity;
import com.fluxcore.approval.entity.ApprovalProcessEntity;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface ApprovalProcessMapper extends BaseMapper<ApprovalProcessEntity> {
    @Select("SELECT * FROM approval_process WHERE business_type=#{businessType} AND status='PUBLISHED' ORDER BY version_no DESC LIMIT 1")
    ApprovalProcessEntity selectPublished(@Param("businessType") String type);
    @Select("SELECT * FROM approval_node WHERE process_id=#{processId} AND node_type='APPROVAL' ORDER BY sequence_no ASC LIMIT 1")
    ApprovalNodeEntity selectFirstApprovalNode(@Param("processId") long id);
    default Optional<ApprovalProcessEntity> findPublished(String type) { return Optional.ofNullable(selectPublished(type)); }
    default Optional<ApprovalNodeEntity> findFirstApprovalNode(long id) { return Optional.ofNullable(selectFirstApprovalNode(id)); }
}
