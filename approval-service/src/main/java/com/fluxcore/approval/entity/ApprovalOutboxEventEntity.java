package com.fluxcore.approval.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("approval_outbox_event")
public class ApprovalOutboxEventEntity {
    @TableId(type=IdType.AUTO) private Long id; private String eventId,aggregateType,aggregateId,eventType,payloadJson,status; private Integer retryCount; private LocalDateTime nextRetryAt,createdAt,publishedAt;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;} public String getAggregateType(){return aggregateType;} public void setAggregateType(String v){aggregateType=v;} public String getAggregateId(){return aggregateId;} public void setAggregateId(String v){aggregateId=v;} public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public String getPayloadJson(){return payloadJson;} public void setPayloadJson(String v){payloadJson=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public Integer getRetryCount(){return retryCount;} public void setRetryCount(Integer v){retryCount=v;} public LocalDateTime getNextRetryAt(){return nextRetryAt;} public void setNextRetryAt(LocalDateTime v){nextRetryAt=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getPublishedAt(){return publishedAt;} public void setPublishedAt(LocalDateTime v){publishedAt=v;}
}
