/* 文件作用：回收预览、开关及客户延期审批的白名单请求。 */
package com.bishe.crm.module.customer.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 客户归属、期限和审批身份均由后端确定。 */
public final class RecycleRequests {
    /** 禁止实例化请求容器。 */
    private RecycleRequests() {}
    /** 管理员预览固定十条分页。 */
    @Getter @Setter
    public static class Query extends PageQuery { @Size(max=160) private String keyword; }
    /** 修改开关需要当前配置版本，避免互相覆盖。 */
    public record Setting(@NotNull Boolean enabled,@NotNull @PositiveOrZero Long version) {}
    /** 发送预警时复核客户版本。 */
    public record Version(@NotNull @PositiveOrZero Long version) {}
    /** 延期需说明原因及后续跟进计划。 */
    public record Appeal(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String reason,@NotBlank @Size(max=500) String nextPlan) {}
    /** 审批只能通过明确动作完成。 */
    public record Review(@NotNull @PositiveOrZero Long version,@NotNull Boolean pass,@NotBlank @Size(max=500) String opinion) {}
}
