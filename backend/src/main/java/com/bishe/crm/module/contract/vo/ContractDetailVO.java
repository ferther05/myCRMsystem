/* 文件作用：返回合同详情、附件及操作历史。 */
package com.bishe.crm.module.contract.vo;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 返回合同详情、附件及操作历史。 */
@Getter
@Setter
public class ContractDetailVO extends ContractSummaryVO {

    private String description;
    private List<ContractAttachmentVO> attachments;
    private List<java.util.Map<String, Object>> operationHistory;
    private List<java.util.Map<String, Object>> signingHistory;
}
