package com.fluxcore.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("notification_record")
public class NotificationRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String receiverId;
    private String channel;
    private String status;
    private Integer retryCount;
    private String payloadJson;
    private String errorMessage;
    private LocalDateTime nextRetryAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
