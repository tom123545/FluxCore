package com.fluxcore.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fluxcore.notification.entity.NotificationRecordEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationRecordMapper extends BaseMapper<NotificationRecordEntity> {
    @Select("SELECT * FROM notification_record WHERE event_id=#{eventId} "
            + "AND receiver_id=#{receiverId} AND channel=#{channel} LIMIT 1")
    NotificationRecordEntity selectByEventReceiverAndChannel(@Param("eventId") String eventId,
                                                             @Param("receiverId") String receiverId,
                                                             @Param("channel") String channel);

    @Select("SELECT * FROM notification_record WHERE status='FAILED' "
            + "AND next_retry_at IS NOT NULL AND next_retry_at <= #{now} "
            + "ORDER BY next_retry_at, id LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    java.util.List<NotificationRecordEntity> selectReadyForRetry(@Param("now") LocalDateTime now,
                                                                  @Param("limit") int limit);

    @Update("UPDATE notification_record SET status='SENT', sent_at=#{sentAt}, "
            + "error_message=NULL, updated_at=#{updatedAt} WHERE id=#{id}")
    int markSent(@Param("id") long id, @Param("sentAt") LocalDateTime sentAt,
                 @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE notification_record SET status='FAILED', retry_count=#{retryCount}, "
            + "next_retry_at=#{nextRetryAt}, error_message=#{errorMessage}, updated_at=#{updatedAt} "
            + "WHERE id=#{id} AND status <> 'SENT'")
    int markFailed(@Param("id") long id, @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);
}
