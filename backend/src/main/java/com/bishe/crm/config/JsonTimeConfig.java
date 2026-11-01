/* 文件作用：API 时间统一显示到秒，数据库仍保留原始精度。 */
package com.bishe.crm.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仅调整输出，不限制原有 ISO 日期时间请求的解析。 */
@Configuration
public class JsonTimeConfig {

    /** 省略 LocalDateTime 响应的小数秒，兼容前端已有 ISO 时间格式。 */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer secondsPrecision() {
        return builder ->
            builder.serializers(
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            );
    }
}
