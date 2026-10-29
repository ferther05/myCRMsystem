/* 文件作用：返回客户列表、归属和数据权限摘要。 */
package com.bishe.crm.module.customer.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 返回字段采用白名单，不包含信用代码、决策链、金额或附件位置。 */
@Getter
@Setter
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

    @JsonIgnore
    private String tagsJson;

    private List<String> tags;
    private Long version;
    private Boolean canEdit;
    private Boolean canDelete;
}
