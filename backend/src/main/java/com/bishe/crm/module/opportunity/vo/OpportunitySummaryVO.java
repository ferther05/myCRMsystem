package com.bishe.crm.module.opportunity.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunitySummaryVO {
    private Long id; private Long customerId; private String customerName; private String opportunityName;
    private Long ownerId; private String ownerName; private Long teamId; private String teamName;
    private String stageCode; private String activityStatus; private Integer stagnationDays; private Integer graceDays;
    private BigDecimal expectedAmount; private LocalDate expectedCloseDate; private LocalDateTime lastEffectiveProgressAt;
    private LocalDateTime stageChangedAt; private LocalDateTime closePendingAt; private Long version;
    private LocalDateTime createdAt; private LocalDateTime updatedAt; private Boolean canEdit; private Boolean canManageCollaborators;
    private Boolean canSetThreshold; private Boolean canDecidePending;
    private Boolean canChangeStage;
}
