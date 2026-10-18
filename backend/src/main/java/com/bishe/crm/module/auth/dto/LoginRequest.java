package com.bishe.crm.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank
    @Size(max = 64)
    private String username;
    @NotBlank
    @Size(max = 72)
    private String password;
}
