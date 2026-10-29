/* 文件作用：返回客户详情、联系人、跟进及权限信息。 */
package com.bishe.crm.module.customer.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 返回客户详情、联系人、跟进及权限信息。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerDetailVO extends CustomerSummaryVO {

    private String unifiedSocialCreditCode;
    private String companySize;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime lastEffectiveFollowAt;
    private LocalDateTime firstContactDeadlineAt;
    private List<ContactVO> contacts;
    private List<FollowRecordVO> followRecords;
    private Boolean canManageContacts;
}
