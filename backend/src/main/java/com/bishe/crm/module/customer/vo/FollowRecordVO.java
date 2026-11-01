/* 文件作用：返回跟进内容、有效性及查验信息。 */
package com.bishe.crm.module.customer.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 返回跟进内容、有效性及查验信息。 */
@Getter
@Setter
public class FollowRecordVO {

    private Long id;
    private Long customerId;
    private Long opportunityId;
    // 提交时的阶段及轮次，回退重入也不能复用旧跟进。
    private String opportunityStageCode;
    private Long opportunityStageSeq;
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
