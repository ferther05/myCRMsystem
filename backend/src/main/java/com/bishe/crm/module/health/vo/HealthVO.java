/* 文件作用：返回整体健康状态及各依赖检查结果。 */
package com.bishe.crm.module.health.vo;

import java.time.OffsetDateTime;
import java.util.Map;

/** 返回整体健康状态及各依赖检查结果。 */
public record HealthVO(String status, Map<String, String> components, OffsetDateTime checkedAt) {}
