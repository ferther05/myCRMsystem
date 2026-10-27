package com.bishe.crm.module.contract.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContractSummaryVO {
    private Long id; private String contractNo; private String externalContractNo;
    private Long customerId; private String customerName; private Long opportunityId; private String opportunityName;
    private Long ownerId; private String ownerName; private Long teamId; private String teamName;
    private String contractName; private String businessType; private String ourPartyName; private String counterpartyName;
    private BigDecimal contractAmount; private LocalDate signedAt; private LocalDate expireDate;
    private String contractStatus; private String signingProgress; private String paymentStatus;
    private BigDecimal receivedAmount; private BigDecimal outstandingAmount; private Long version;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
    private Boolean canEdit; private Boolean canSubmit; private Boolean canReview;
    private Boolean canUpdateSigningProgress; private Boolean canCorrectSigningProgress;
}
