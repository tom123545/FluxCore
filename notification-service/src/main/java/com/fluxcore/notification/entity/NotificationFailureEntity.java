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
@TableName("notification_failure")
public class NotificationFailureEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private String rawMessage;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
