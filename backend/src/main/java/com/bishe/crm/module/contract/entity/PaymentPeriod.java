/* 文件作用：映射合同回款计划中的单个期次。 */
package com.bishe.crm.module.contract.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 回款计划期次实体。 */
@Data
public class PaymentPeriod {
    private Long id;
    private Long planId;
    private Integer periodNo;
    private BigDecimal plannedAmount;
    private LocalDate dueDate;
    private BigDecimal receivedAmount;
    private String periodStatus;
    private Long replacedByPlanId;
    private Long version;
    private String triggerNode;
}
