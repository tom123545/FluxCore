package com.fluxcore.approval.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.approval.entity.ApprovalOutboxEventEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalOutboxEventMapper extends BaseMapper<ApprovalOutboxEventEntity> {
    @Select("SELECT * FROM approval_outbox_event "
            + "WHERE status IN ('NEW', 'FAILED') "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "ORDER BY id ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<ApprovalOutboxEventEntity> selectReadyForPublish(@Param("now") LocalDateTime now,
                                                           @Param("limit") int limit);

    @Update("UPDATE approval_outbox_event SET status='PUBLISHED', published_at=#{publishedAt}, "
            + "next_retry_at=NULL WHERE id=#{id} AND status IN ('NEW', 'FAILED')")
    int markPublished(@Param("id") long id, @Param("publishedAt") LocalDateTime publishedAt);

    @Update("UPDATE approval_outbox_event SET status='FAILED', retry_count=#{retryCount}, "
            + "next_retry_at=#{nextRetryAt} WHERE id=#{id} AND status IN ('NEW', 'FAILED')")
    int markFailed(@Param("id") long id, @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);
}
