/* 文件作用：真实MySQL/Redis验证列表缓存角色隔离、事务失效与商机合同精确定位。 */
package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import com.bishe.crm.module.analytics.service.DashboardCache;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.opportunity.mapper.OpportunityMapper;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class QueryNavigationIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired SystemUserMapper users;
    @Autowired JwtTokenService tokens;
    @Autowired Clock clock;
    @Autowired DashboardCache revisions;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired OpportunityMapper opportunities;
    @Autowired PlatformTransactionManager transactions;
    PaymentFlowIT f;
    @BeforeEach void setup() {
        f=new PaymentFlowIT(); f.http=http; f.jdbc=jdbc; f.users=users; f.tokens=tokens; f.businessClock=clock;
        f.setup(); revisions.invalidate();
    }
    @AfterEach void cleanup() { f.cleanup(); revisions.invalidate(); }
    JsonNode get(Long user,String path) {
        var headers=new HttpHeaders(); headers.setBearerAuth(tokens.issue(AuthPrincipal.from(users.findWithTeamById(user))).value());
        var response=http.exchange("/api"+path,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("code").asInt()).isZero(); return response.getBody().path("data");
    }
    @Test void listCacheSeparatesActorsAndInvalidatesOnlyAfterCommit() {
        String path="/opportunities?keyword="+f.prefix;
        assertThat(get(f.sales,path).path("total").asInt()).isEqualTo(1);
        var keys=redis.keys("crm:query:opportunities:v1:*");
        assertThat(keys).isNotEmpty();
        assertThat(keys.stream().map(key->redis.opsForValue().get(key)).filter(java.util.Objects::nonNull))
            .anyMatch(value->value.contains(f.prefix));
        assertThat(get(f.peer,path).path("total").asInt()).isZero();
        assertThat(get(f.manager,path).path("total").asInt()).isEqualTo(1);
        assertThat(get(f.admin,path).path("total").asInt()).isEqualTo(1);
        String before=revisions.key("transaction-proof");
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{
            var row=opportunities.find(f.opportunity); row.setOpportunityName(f.prefix+"-rolled-back");
            opportunities.updateBasic(row,LocalDateTime.now(clock)); status.setRollbackOnly();
        });
        assertThat(revisions.key("transaction-proof")).isEqualTo(before);
        assertThat(get(f.sales,path).path("list").get(0).path("opportunityName").asText()).isEqualTo(f.prefix);
        tx.executeWithoutResult(status->{
            var row=opportunities.find(f.opportunity); row.setOpportunityName(f.prefix+"-committed");
            opportunities.updateBasic(row,LocalDateTime.now(clock));
        });
        assertThat(revisions.key("transaction-proof")).isNotEqualTo(before);
        assertThat(get(f.sales,path).path("list").get(0).path("opportunityName").asText()).endsWith("-committed");
        assertThat(get(f.sales,path+"&refresh=true").path("total").asInt()).isEqualTo(1);
    }
    @Test void exactContractScopeDoesNotExpandPermissions() {
        String path="/contracts?opportunityId="+f.opportunity;
        assertThat(get(f.sales,path).path("list").get(0).path("id").asLong()).isEqualTo(f.contract);
        assertThat(get(f.peer,path).path("total").asInt()).isZero();
        assertThat(get(f.manager,path).path("total").asInt()).isEqualTo(1);
        assertThat(get(f.admin,"/contracts?opportunityId=9223372036854775807").path("total").asInt()).isZero();
        jdbc.update("UPDATE crm_opportunity SET stage_code='DEAL' WHERE id=?",f.opportunity);
        assertThat(get(f.sales,"/contracts/registration-opportunities?opportunityId="+f.opportunity).path("total").asInt()).isEqualTo(1);
        assertThat(get(f.peer,"/contracts/registration-opportunities?opportunityId="+f.opportunity).path("total").asInt()).isZero();
    }
}

