package com.bishe.crm.module.system.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ApplicationVO {
    private Long id;
    private Long bizId;
    private String username;
    private String realName;
    private Long teamId;
    private String teamName;
    private String applicantName;
    private String approvalStatus;
    private String reason;
    private String reviewOpinion;
    private Long version;
    private LocalDateTime submittedAt;
    private LocalDateTime processedAt;
}
