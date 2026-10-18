package com.bishe.crm.module.system.dto;

import jakarta.validation.constraints.*;

public final class UserRequests {
    private UserRequests() { }

    public record Profile(@NotBlank @Size(max=80) String realName,
                          @Pattern(regexp="^$|^[0-9+() -]{6,32}$") String phone,
                          @Email @Size(max=160) String email) { }

    public record Create(@NotBlank @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username,
                         @NotBlank @Size(max=80) String realName,
                         @Pattern(regexp="^$|^[0-9+() -]{6,32}$") String phone,
                         @Email @Size(max=160) String email,
                         @NotBlank @Pattern(regexp="ADMIN|MANAGER|SPECIALIST") String roleCode,
                         @Positive Long teamId,
                         @NotBlank @Size(min=8,max=64)
                         @Pattern(regexp="^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$") String initialPassword) { }

    public record Access(@NotNull @Min(0) Long version,
                         @NotBlank @Pattern(regexp="ADMIN|MANAGER|SPECIALIST") String roleCode,
                         @Positive Long teamId,
                         @NotBlank @Pattern(regexp="ACTIVE|DISABLED") String accountStatus) { }

    public record Reset(@NotNull @Min(0) Long version,
                        @NotBlank @Size(min=8,max=64)
                        @Pattern(regexp="^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$") String initialPassword) { }

    public record Apply(@NotBlank @Pattern(regexp="^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username,
                        @NotBlank @Size(max=80) String realName,
                        @Pattern(regexp="^$|^[0-9+() -]{6,32}$") String phone,
                        @Email @Size(max=160) String email,
                        @NotBlank @Size(max=1000) String reason) { }

    public record Review(@NotNull @Min(0) Long version, @NotNull Boolean approved,
                         @NotBlank @Size(max=500) String opinion,
                         @Size(min=8,max=64)
                         @Pattern(regexp="^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$") String initialPassword) { }
}
