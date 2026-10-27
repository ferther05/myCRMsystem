package com.bishe.crm.module.customer.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
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
