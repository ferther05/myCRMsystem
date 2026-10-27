package com.bishe.crm.module.contract.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 成交商单及其当前登记情况；尚未创建登记时 contractId 为空。 */
@Getter @Setter
public class ContractRegistrationVO {
    private Long opportunityId;
    private String opportunityName;
    private Long customerId;
    private String customerName;
    private Long ownerId;
    private String ownerName;
    private Long teamId;
    private String teamName;
    private BigDecimal expectedAmount;
    private LocalDate expectedCloseDate;
    private Long contractId;
    private String contractNo;
    private String contractStatus;
    private String signingProgress;
    private Boolean canUpload;
}
