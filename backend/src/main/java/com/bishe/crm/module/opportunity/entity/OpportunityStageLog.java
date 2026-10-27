package com.bishe.crm.module.opportunity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_opportunity_stage_log")
public class OpportunityStageLog {
    @TableId(type=IdType.AUTO) private Long id;
    private Long opportunityId;
    private String fromStage;
    private String toStage;
    private String actionCode;
    private String reason;
    private Long operatorId;
    private LocalDateTime occurredAt;
}
