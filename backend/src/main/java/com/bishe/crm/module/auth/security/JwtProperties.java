/* 文件作用：JWT 签名和有效期配置。 */
package com.bishe.crm.module.auth.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** JWT 签名和有效期配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "crm.security.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer = "crm-backend";

    @Min(60)
    @Max(86400)
    private long ttlSeconds = 7200;
}
