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
    @Select("SELECT * FROM notification_record WHERE event_id=#{eventId} LIMIT 1")
    NotificationRecordEntity selectByEventId(@Param("eventId") String eventId);

    @Update("UPDATE notification_record SET status='SENT', sent_at=#{sentAt}, "
            + "error_message=NULL, updated_at=#{updatedAt} WHERE id=#{id}")
    int markSent(@Param("id") long id, @Param("sentAt") LocalDateTime sentAt,
                 @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE notification_record SET status='FAILED', retry_count=#{retryCount}, "
            + "error_message=#{errorMessage}, updated_at=#{updatedAt} WHERE id=#{id}")
    int markFailed(@Param("id") long id, @Param("retryCount") int retryCount,
                   @Param("errorMessage") String errorMessage, @Param("updatedAt") LocalDateTime updatedAt);
}
