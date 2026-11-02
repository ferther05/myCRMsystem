/* 文件作用：看板日期及负责人筛选，数据范围仍由认证身份决定。 */
package com.bishe.crm.module.analytics.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Positive;

@Getter @Setter
public class DashboardQuery {
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate from;
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate to;
    @Positive private Long ownerId;
    private boolean refresh;
}
