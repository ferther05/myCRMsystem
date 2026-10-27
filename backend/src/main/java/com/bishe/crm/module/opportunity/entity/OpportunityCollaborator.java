package com.bishe.crm.module.opportunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_opportunity_collaborator")
public class OpportunityCollaborator {
    @TableId(type=IdType.AUTO) private Long id;
    private Long opportunityId;
    private Long userId;
    private String collaboratorRole;
    private BigDecimal splitRatio;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private String collaboratorStatus;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
