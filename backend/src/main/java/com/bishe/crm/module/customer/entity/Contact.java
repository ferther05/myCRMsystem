package com.bishe.crm.module.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_contact")
public class Contact {
    @TableId(type=IdType.AUTO) private Long id;
    private Long customerId;
    private String contactName;
    private String jobTitle;
    private String phone;
    private String email;
    private LocalDate birthday;
    private String decisionRole;
    private Boolean isPrimary;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
