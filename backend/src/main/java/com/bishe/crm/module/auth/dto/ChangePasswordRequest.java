package com.bishe.crm.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    @NotBlank
    @Size(max = 72)
    private String oldPassword;
    @NotBlank
    @Size(min = 8, max = 64)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$")
    private String newPassword;
    @NotBlank
    @Size(max = 64)
    private String confirmPassword;
}
