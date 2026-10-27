package com.bishe.crm.module.customer.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FollowRecordVO {
    private Long id;
    private Long customerId;
    private Long opportunityId;
    private Long contactId;
    private String contactName;
    private Long followUserId;
    private String followUserName;
    private String followMethod;
    private String content;
    private String resultCode;
    private String substantiveResult;
    private String nextAction;
    private LocalDateTime nextActionAt;
    private Boolean effective;
    private String reviewStatus;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String reviewReason;
    private LocalDateTime followedAt;
    private Long version;
}
