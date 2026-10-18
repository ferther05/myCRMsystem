package com.bishe.crm.module.customer.vo;

import lombok.Getter;
import lombok.Setter;

/** Explicit field whitelist: no credit code, decision chain, amounts, or attachment locations. */
@Getter @Setter
public class CustomerSummaryVO {
    private Long id;
    private String customerName;
    private String primaryPhone;
    private String sourceCode;
    private String industryCode;
    private String customerStatus;
    private String seaType;
    private Long ownerId;
    private String ownerName;
    private String teamName;
    private Boolean readonly;
}
