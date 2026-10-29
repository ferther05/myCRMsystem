/* 文件作用：返回各商机阶段的数量和预计金额。 */
package com.bishe.crm.module.opportunity.vo;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 返回各商机阶段的数量和预计金额。 */
@Getter
@Setter
public class OpportunityFunnelVO {

    private String stageCode;
    private String stageName;
    private Long opportunityCount;
    private BigDecimal expectedAmount;
}
