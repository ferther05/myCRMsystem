/* 文件作用：返回登录令牌及用户资料。 */
package com.bishe.crm.module.auth.vo;

import java.time.Instant;

/** 返回登录令牌及用户资料。 */
public record LoginVO(
    String accessToken,
    String tokenType,
    long expiresIn,
    Instant expiresAt,
    CurrentUserVO user
) {}
