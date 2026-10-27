package com.bishe.crm.module.opportunity.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class OpportunityRequests {
    private OpportunityRequests() {}
    public record Create(@NotNull @Positive Long customerId, @NotBlank @Size(max=160) String opportunityName,
                         @PositiveOrZero @NotNull @Digits(integer=16, fraction=2) BigDecimal expectedAmount, @NotNull LocalDate expectedCloseDate,
                         @Positive Long ownerId) {}
    public record Edit(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max=160) String opportunityName,
                       @PositiveOrZero @NotNull @Digits(integer=16, fraction=2) BigDecimal expectedAmount, @NotNull LocalDate expectedCloseDate) {}
    public record Evidence(@NotNull @PositiveOrZero Long version,
                           @NotBlank @Pattern(regexp="QUOTE|CONFIRMATION") String kind,
                           @PositiveOrZero @Digits(integer=16,fraction=2) BigDecimal quoteAmount,
                           @NotBlank @Size(max=500) String description) {}
    public record PendingDecision(@NotNull @PositiveOrZero Long version, boolean close,
                                  @NotBlank @Size(max=500) String reason) {}
    public record StageAction(@NotNull @PositiveOrZero Long version, @NotBlank @Pattern(regexp="NEW|FOLLOWING|QUOTED|DEAL|WON|LOST|CLOSED") String targetStage,
                              @Size(max=500) String reason) {}
    public record Threshold(@NotNull @PositiveOrZero Long version, @NotNull @Min(1) @Max(3650) Integer stagnationDays,
                            @NotNull @Min(0) @Max(365) Integer graceDays) {}
    public record CollaboratorWrite(@NotNull @PositiveOrZero Long version, @NotNull @Positive Long userId,
                                    @NotNull @DecimalMin("0.01") @DecimalMax("30.00") @Digits(integer=2,fraction=2) BigDecimal splitRatio) {}
    public record CollaboratorRemove(@NotNull @PositiveOrZero Long version, @NotNull @Positive Long collaboratorId) {}
}
