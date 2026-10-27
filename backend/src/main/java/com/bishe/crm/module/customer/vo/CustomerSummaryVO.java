package com.bishe.crm.module.customer.vo;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** Explicit field whitelist: no credit code, decision chain, amounts, or attachment locations. */
@Getter @Setter
public class CustomerSummaryVO {
    private Long id;
    private String customerName;
    private String primaryPhone;
    private String sourceCode;
    private String industryCode;
    private String customerStatus;
    private String seaType;
    private Long ownerId;
    private String ownerName;
    private String teamName;
    private Boolean readonly;
    private Long teamId;
    private String primaryContactName;
    private String customerLevel;
    private String region;
    @JsonIgnore private String tagsJson;
    private List<String> tags;
    private Long version;
    private Boolean canEdit;
    private Boolean canDelete;
}
