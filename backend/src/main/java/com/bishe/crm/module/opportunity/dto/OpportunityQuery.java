/* 文件作用：商机列表的阶段、负责人和分页筛选条件。 */
package com.bishe.crm.module.opportunity.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 商机列表的阶段、负责人和分页筛选条件。 */
@Getter
@Setter
public class OpportunityQuery extends PageQuery {

    @Size(max = 160)
    private String keyword;

    @Pattern(regexp = "NEW|FOLLOWING|QUOTED|DEAL|WON|LOST|CLOSED")
    private String stageCode;

    @Pattern(regexp = "ACTIVE|STAGNANT|CLOSE_PENDING|TERMINATED")
    private String activityStatus;

    private Long ownerId;

    /** 手动刷新时绕过短时查询缓存。 */
    private boolean refresh;
}
