/* 文件作用：返回阶段轨迹及下一阶段必须满足的条件。 */
package com.bishe.crm.module.opportunity.vo;

import java.util.List;

/** 只返回门槛状态，不扩大合同或客户资料的读取权限。 */
public record OpportunityStageProgressVO(
    String stageCode,
    String stageName,
    String description,
    boolean current,
    String status,
    String nextStage,
    String nextStageName,
    List<Requirement> requirements,
    boolean ready
) {
    /** 单项阶段门槛及当前满足情况。 */
    public record Requirement(String code, String label, boolean met, String action) {}
}
