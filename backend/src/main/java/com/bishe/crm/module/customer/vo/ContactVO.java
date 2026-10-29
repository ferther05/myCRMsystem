/* 文件作用：返回联系人资料与可编辑状态。 */
package com.bishe.crm.module.customer.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** 返回联系人资料与可编辑状态。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactVO {

    private Long id;
    private String contactName;
    private String phone;
    private String email;
    private String jobTitle;
    private LocalDate birthday;
    private String decisionRole;
    private Boolean primary;
    private Long version;
}
