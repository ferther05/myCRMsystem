/* 文件作用：客户及商机跟进记录实体，保留有效性和查验结果。 */
package com.bishe.crm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 客户及商机跟进记录实体，保留有效性和查验结果。 */
@Getter
@Setter
@TableName("crm_follow_record")
public class FollowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;
    private Long opportunityId;
    // 提交时的阶段及轮次，回退重入也不能复用旧跟进。
    private String opportunityStageCode;
    private Long opportunityStageSeq;
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
