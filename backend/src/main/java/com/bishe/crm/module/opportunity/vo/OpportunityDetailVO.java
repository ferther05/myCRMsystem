package com.bishe.crm.module.opportunity.vo;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunityDetailVO extends OpportunitySummaryVO {
    private List<OpportunityCollaboratorVO> collaborators;
    private List<OpportunityStageLogVO> stageHistory;
    private List<OpportunityStageProgressVO> stageProgress;
    private java.math.BigDecimal quoteAmount;
    private java.time.LocalDateTime quoteAt;
    private String customerConfirmResult;
    private java.time.LocalDateTime customerConfirmAt;
    private Long customerVersion;
    private List<java.util.Map<String,Object>> contacts;
    private List<com.bishe.crm.module.customer.vo.FollowRecordVO> followRecords;
    private List<java.util.Map<String,Object>> operationHistory;
}
