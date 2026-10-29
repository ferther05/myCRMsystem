/* 文件作用：提供使用上海时区的业务时钟。 */
package com.bishe.crm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 提供使用上海时区的业务时钟。 */
@Configuration
public class BusinessTimeConfig {

    /** 创建使用 Asia/Shanghai 时区的业务时钟。 */
    @Bean
    public Clock businessClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
