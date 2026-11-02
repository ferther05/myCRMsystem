/* 文件作用：复用通用审批表保存客户回收延期审批与历史。 */
package com.bishe.crm.module.customer.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 审批结论和原周期快照不可由客户表单覆盖。 */
@Getter @Setter
public class RecycleAppeal {
    private Long id;
    private Long bizId;
    private Long applicantId;
    private Long approverId;
    private String approvalStatus;
    private String reason;
    private String extraJson;
    private Long version;
    private LocalDateTime submittedAt;
    private LocalDateTime processedAt;
    private String applicantName;
    private String approverName;
}
