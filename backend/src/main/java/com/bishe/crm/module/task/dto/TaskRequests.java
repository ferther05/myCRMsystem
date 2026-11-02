/* 文件作用：只接收待办已读版本和管理员扫描的白名单参数。 */
package com.bishe.crm.module.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** 不允许客户端直接修改待办业务状态或指定补跑对象。 */
public final class TaskRequests {
    private TaskRequests() {}
    /** 将本人提醒标记为已读，不代替办理业务。 */
    public record Read(@NotNull @PositiveOrZero Long version) {}
    /** 手动扫描仅支持已实现任务，业务日期取服务器当天。 */
    public record Scan(@NotBlank @Pattern(regexp = "opportunity-activity-scan|payment-overdue-scan|customer-recycle-scan|business-reminder-scan") String jobName) {}
}
