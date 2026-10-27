package com.bishe.crm.module.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_contract")
public class Contract {
    @TableId(type = IdType.AUTO) private Long id;
    private String contractNo;
    private String externalContractNo;
    private Long customerId;
    private Long opportunityId;
    private Long ownerId;
    private Long teamId;
    private String contractName;
    private String businessType;
    private String ourPartyName;
    private String counterpartyName;
    private String description;
    private BigDecimal contractAmount;
    private LocalDate signedAt;
    private LocalDate expireDate;
    private String contractStatus;
    private String signingProgress;
    private String paymentStatus;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;
    private Long version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
