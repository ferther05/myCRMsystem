/* 文件作用：验证 Redis 不可用时的真实 HTTP 降级响应。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** 验证 Redis 不可用时的真实 HTTP 降级响应。 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.data.redis.port=1"
)
@ActiveProfiles("local")
class BackendDegradedHealthIT {

    @Autowired
    private TestRestTemplate http;

    /** 验证 Redis 故障时真实 HTTP 返回 503 且隐藏连接细节。 */
    @Test
    void unavailableRedisReturns503OverRealHttpWithoutLeakingConnectionDetails() {
        // 仅隔离测试实例连接不可用端口，项目 Redis 服务保持运行。
        ResponseEntity<JsonNode> response = http.getForEntity("/api/health", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("code").asInt()).isEqualTo(50300);
        assertThat(body.path("data").path("status").asText()).isEqualTo("DOWN");
        assertThat(body.path("data").path("components").path("mysql").asText()).isEqualTo("UP");
        assertThat(body.path("data").path("components").path("redis").asText()).isEqualTo("DOWN");
        assertThat(body.toString()).doesNotContain("jdbc:", "password", "Exception", "127.0.0.1");
        assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
    }
}
