package com.bishe.crm.module.opportunity.vo;

import java.util.List;

/** 只返回门槛状态，不扩大合同或客户资料的读取权限。 */
public record OpportunityStageProgressVO(String stageCode, String stageName, String description,
        boolean current, String status, String nextStage, String nextStageName,
        List<Requirement> requirements, boolean ready) {
    public record Requirement(String code, String label, boolean met, String action) {}
}
