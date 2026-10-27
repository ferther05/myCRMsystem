package com.bishe.crm.module.opportunity.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunityCollaboratorVO {
    private Long id; private Long userId; private String realName; private String roleCode; private Long teamId;
    private BigDecimal splitRatio; private String collaboratorRole; private String collaboratorStatus;
    private LocalDateTime joinedAt; private LocalDateTime leftAt; private Long version;
}
