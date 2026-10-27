package com.bishe.crm.module.opportunity.dto;

import com.bishe.crm.common.api.PageQuery;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OpportunityQuery extends PageQuery {
    @Size(max=160) private String keyword;
    @Pattern(regexp="NEW|FOLLOWING|QUOTED|DEAL|WON|LOST|CLOSED") private String stageCode;
    @Pattern(regexp="ACTIVE|STAGNANT|CLOSE_PENDING|TERMINATED") private String activityStatus;
    private Long ownerId;
}
