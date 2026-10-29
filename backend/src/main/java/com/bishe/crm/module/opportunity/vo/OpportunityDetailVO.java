/* 文件作用：返回商机详情、证据、阶段条件和权限。 */
package com.bishe.crm.module.opportunity.vo;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 返回商机详情、证据、阶段条件和权限。 */
@Getter
@Setter
public class OpportunityDetailVO extends OpportunitySummaryVO {

    private List<OpportunityCollaboratorVO> collaborators;
    private List<OpportunityStageLogVO> stageHistory;
    private List<OpportunityStageProgressVO> stageProgress;
    private java.math.BigDecimal quoteAmount;
    private java.time.LocalDateTime quoteAt;
    private String customerConfirmResult;
    private java.time.LocalDateTime customerConfirmAt;
    private Long customerVersion;
    private List<java.util.Map<String, Object>> contacts;
    private List<com.bishe.crm.module.customer.vo.FollowRecordVO> followRecords;
    private List<java.util.Map<String, Object>> operationHistory;
}
