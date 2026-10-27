package com.bishe.crm.module.contract.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ContractRequests {
    private ContractRequests() {}
    public record Create(@NotNull @Positive Long opportunityId) {}
    public record Edit(@NotNull @PositiveOrZero Long version,
                       @NotBlank @Size(max = 160) String contractName,
                       @Size(max = 64) String externalContractNo,
                       @Size(max = 64) String businessType,
                       @Size(max = 160) String ourPartyName,
                       @Size(max = 160) String counterpartyName,
                       @NotNull @PositiveOrZero @Digits(integer = 16, fraction = 2) BigDecimal contractAmount,
                       LocalDate signedAt, LocalDate expireDate,
                       @Size(max = 1000) String description) {}
    public record Submit(@NotNull @PositiveOrZero Long version) {}
    public record Review(@NotNull @PositiveOrZero Long version, boolean pass,
                          @NotBlank @Size(max = 1000) String opinion) {}
    public record SigningProgress(@NotNull @PositiveOrZero Long version,
                                 @NotBlank @Pattern(regexp="INTERNAL_HANDLING|WAIT_OUR_SIGN|WAIT_COUNTERPARTY_SIGN|BOTH_SIGNED|EXCEPTION|WITHDRAWN") String targetProgress,
                                 @NotBlank @Size(max=500) String reason) {}
}
