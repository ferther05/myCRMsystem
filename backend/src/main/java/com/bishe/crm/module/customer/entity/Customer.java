package com.bishe.crm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_customer")
public class Customer {
    @TableId(type=IdType.AUTO) private Long id;
    private String customerName;
    private String normalizedName;
    private String unifiedSocialCreditCode;
    private String primaryPhone;
    private String normalizedPrimaryPhone;
    private String sourceCode;
    private String industryCode;
    private String region;
    private String companySize;
    private String remark;
    private String customerLevel;
    private String tagsJson;
    private Long ownerId;
    private Long teamId;
    private String customerStatus;
    private String seaType;
    private Long ownershipSeq;
    private Long previousOwnerId;
    private LocalDateTime returnPriorityUntil;
    private Long version;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime warningAt;
    private LocalDateTime firstContactDeadlineAt;
    private LocalDateTime lastEffectiveFollowAt;
    private Boolean deleted;
}
