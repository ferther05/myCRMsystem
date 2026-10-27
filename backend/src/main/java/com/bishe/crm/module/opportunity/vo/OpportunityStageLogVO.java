package com.bishe.crm.module.opportunity.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunityStageLogVO {
    private Long id; private String fromStage; private String toStage; private String actionCode; private String reason;
    private Long operatorId; private String operatorName; private LocalDateTime occurredAt;
}
