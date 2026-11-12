/* 文件作用：集中定义账号管理、个人资料与申请审核的请求参数。 */
package com.bishe.crm.module.system.dto;

import jakarta.validation.constraints.*;

/** 集中定义账号管理、个人资料与申请审核的请求参数。 */
public final class UserRequests {

    /** 禁止实例化请求参数容器。 */
    private UserRequests() {}

    /** 账号基本资料请求参数及校验约束。 */
    public record Profile(
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "^$|^[0-9+() -]{6,32}$") String phone,
        @Email @Size(max = 160) String email
    ) {}

    /** 账号新建请求参数及校验约束。 */
    public record Create(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username,
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "^$|^[0-9+() -]{6,32}$") String phone,
        @Email @Size(max = 160) String email,
        @NotBlank @Pattern(regexp = "ADMIN|MANAGER|SPECIALIST") String roleCode,
        @Positive Long teamId,
        @Size(max = 100) String newTeamName,
        @NotBlank @Size(min = 8, max = 64) @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$"
        ) String initialPassword
    ) {}

    /** 账号角色、团队及启停调整请求参数及校验约束。 */
    public record Access(
        @NotNull @Min(0) Long version,
        @NotBlank @Pattern(regexp = "ADMIN|MANAGER|SPECIALIST") String roleCode,
        @Positive Long teamId,
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED") String accountStatus
    ) {}

    /** 账号初始密码重置请求参数及校验约束。 */
    public record Reset(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(min = 8, max = 64) @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$"
        ) String initialPassword
    ) {}

    /** 账号内部账号申请请求参数及校验约束。 */
    public record Apply(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username,
        @NotBlank @Size(max = 80) String realName,
        @Pattern(regexp = "^$|^[0-9+() -]{6,32}$") String phone,
        @Email @Size(max = 160) String email,
        @NotBlank @Size(max = 1000) String reason
    ) {}

    /** 账号审核请求参数及校验约束。 */
    public record Review(
        @NotNull @Min(0) Long version,
        @NotNull Boolean approved,
        @NotBlank @Size(max = 500) String opinion,
        @Size(min = 8, max = 64) @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*[0-9])[!-~]+$"
        ) String initialPassword
    ) {}
}
