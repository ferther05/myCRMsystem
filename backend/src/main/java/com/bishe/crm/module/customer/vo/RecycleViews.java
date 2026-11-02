/* 文件作用：返回可解释的回收判定、期限、保护原因与延期历史。 */
package com.bishe.crm.module.customer.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 公海页面不会获得私海业务保护详情。 */
public final class RecycleViews {
    /** 禁止实例化视图容器。 */
    private RecycleViews() {}
    /** 可复用的单客户判定结果。 */
    public record Rule(String cycleKey,LocalDateTime warningAt,LocalDateTime deadlineAt,String decision,String reason,boolean warned) {}
    /** 管理员候选列表。 */
    public record Row(Long id,String customerName,String ownerName,String teamName,Long version,Rule rule) {}
    /** 客户详情的延期记录，隐藏内部JSON快照。 */
    public record Appeal(Long id,String status,String reason,String nextPlan,String opinion,String applicantName,String approverName,LocalDateTime submittedAt,LocalDateTime processedAt,Long version,boolean canReview) {}
    /** 只返回预警、延期、回收与重新领取的摘要历史。 */
    public record Event(Long id,String action,String operatorName,LocalDateTime occurredAt,String reason) {}
    /** 当前规则与可执行动作。 */
    public record Detail(Rule rule,boolean canAppeal,boolean canWarn,List<Appeal> appeals,List<Event> events) {}
    /** 自动回收初始关闭。 */
    public record Setting(boolean enabled,Long version) {}
}
