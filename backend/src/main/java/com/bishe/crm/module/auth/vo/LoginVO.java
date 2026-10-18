package com.bishe.crm.module.auth.vo;

import java.time.Instant;

public record LoginVO(String accessToken, String tokenType, long expiresIn, Instant expiresAt,
                      CurrentUserVO user) {
}
