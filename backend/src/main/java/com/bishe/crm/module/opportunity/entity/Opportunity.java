package com.bishe.crm.module.opportunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_opportunity")
public class Opportunity {
    @TableId(type=IdType.AUTO) private Long id;
    private Long customerId;
    private String opportunityName;
    private Long ownerId;
    private Long teamId;
    private String stageCode;
    private String activityStatus;
    private Integer stagnationDays;
    private Integer graceDays;
    private Boolean thresholdCustomized;
    private BigDecimal expectedAmount;
    private LocalDate expectedCloseDate;
    private BigDecimal quoteAmount;
    private LocalDateTime quoteAt;
    private String customerConfirmResult;
    private LocalDateTime customerConfirmAt;
    private LocalDateTime lastEffectiveProgressAt;
    private LocalDateTime stageChangedAt;
    private LocalDateTime closePendingAt;
    private LocalDateTime terminalAt;
    private String closeReason;
    private LocalDateTime signedConfirmedAt;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
