/* 文件作用：工作台只读汇总及安全业务定位，不返回业务敏感快照。 */
package com.bishe.crm.module.workbench.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class WorkbenchViews {
    private WorkbenchViews() {}
    public record Metric(String key, String title, BigDecimal value, String unit, String note, String category) {}
    public record Category(String key, String title, long count) {}
    public record Item(String key, String type, String title, String objectName, String actorName,
                       LocalDateTime occurredAt, BigDecimal amount, String target) {}
    public record Overview(String role, String scope, LocalDateTime generatedAt, List<Metric> metrics,
                           List<Category> categories) {}
}
