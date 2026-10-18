package com.bishe.crm.module.health.vo;

import java.time.OffsetDateTime;
import java.util.Map;

public record HealthVO(String status, Map<String, String> components, OffsetDateTime checkedAt) {
}
