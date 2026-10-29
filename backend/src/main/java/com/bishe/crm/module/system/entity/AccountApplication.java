/* 文件作用：内部账号申请及审核状态的数据库实体。 */
package com.bishe.crm.module.system.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 内部账号申请及审核状态的数据库实体。 */
@Getter
@Setter
public class AccountApplication {

    private Long id;
    private Long bizId;
    private Long applicantId;
    private Long approverId;
    private String approvalStatus;
    private String reason;
    private String reviewOpinion;
    private Long version;
    private String username;
    private String realName;
    private String teamName;
    private Long teamId;
    private String applicantName;
    private LocalDateTime submittedAt;
    private LocalDateTime processedAt;
}
