/* 文件作用：Spring Boot 后端启动入口。 */
package com.bishe.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot 后端启动入口。 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class CrmApplication {

    /** 启动 CRM 后端及 Spring 容器。 */
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
