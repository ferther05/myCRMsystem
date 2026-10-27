package com.bishe.crm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_follow_record")
public class FollowRecord {
    @TableId(type=IdType.AUTO) private Long id;
    private Long customerId;
    private Long opportunityId;
    private Long contactId;
    private Long followUserId;
    private String recordType;
    private String followMethod;
    private String content;
    private String substantiveResult;
    private String resultCode;
    private String nextAction;
    private LocalDateTime nextActionAt;
    private Boolean isEffective;
    private String reviewStatus;
    private Long reviewerId;
    private LocalDateTime reviewedAt;
    private String reviewReason;
    private LocalDateTime followedAt;
    private LocalDateTime createdAt;
    private Long version;
}
