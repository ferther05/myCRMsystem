package com.bishe.crm.module.contract.vo;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContractDetailVO extends ContractSummaryVO {
    private String description;
    private List<ContractAttachmentVO> attachments;
    private List<java.util.Map<String, Object>> operationHistory;
    private List<java.util.Map<String, Object>> signingHistory;
}
