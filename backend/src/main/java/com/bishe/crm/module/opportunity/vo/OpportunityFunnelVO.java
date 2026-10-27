package com.bishe.crm.module.opportunity.vo;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunityFunnelVO {
    private String stageCode; private String stageName; private Long opportunityCount; private BigDecimal expectedAmount;
}
