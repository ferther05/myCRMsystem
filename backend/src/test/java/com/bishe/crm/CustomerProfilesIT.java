package com.bishe.crm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.mapper.CustomerMapper;
import com.bishe.crm.module.system.mapper.SystemUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class CustomerProfilesIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired SystemUserMapper users;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean CustomerMapper customers;
    String prefix;
    Long east,south,admin,manager,sales,peer,foreign;
    List<Long> actors = new ArrayList<>();
    Map<String,String> originalClaimLimits;

    @BeforeEach void setup() {
        originalClaimLimits = new HashMap<>();
        jdbc.query("SELECT config_key,config_value FROM sys_config WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key IN ('claim_hold_limit','claim_daily_limit')",
                row -> { originalClaimLimits.put(row.getString(1),row.getString(2)); });
        prefix="cp-"+UUID.randomUUID().toString().substring(0,8);
        east=team("east"); south=team("south");
        admin=user("admin","ADMIN",null); manager=user("manager","MANAGER",east);
        sales=user("sales","SPECIALIST",east); peer=user("peer","SPECIALIST",east); foreign=user("foreign","SPECIALIST",south);
    }
    @AfterEach void cleanup() {
        reset(customers);
        List<Long> ids = jdbc.queryForList("SELECT id FROM crm_customer WHERE created_by IN (?,?,?,?,?)",Long.class,admin,manager,sales,peer,foreign);
        for(long id:ids) {
            jdbc.update("DELETE FROM crm_follow_record WHERE customer_id=?",id);
            jdbc.update("DELETE FROM crm_opportunity WHERE customer_id=?",id);
            jdbc.update("DELETE FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",id);
            jdbc.update("DELETE FROM crm_contact WHERE customer_id=?",id);
            jdbc.update("DELETE FROM crm_customer WHERE id=?",id);
        }
        for(long actor:actors) jdbc.update("DELETE FROM crm_operation_log WHERE operator_id=?",actor);
        jdbc.update("DELETE FROM sys_config WHERE config_type='DICT' AND config_key LIKE ?",prefix.toUpperCase(Locale.ROOT).replace('-','_')+"%");
        for(long actor:actors) jdbc.update("DELETE FROM sys_user WHERE id=?",actor);
        jdbc.update("DELETE FROM sys_team WHERE id IN (?,?)",east,south);
        originalClaimLimits.forEach((key,value) -> jdbc.update("UPDATE sys_config SET config_value=? WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key=?",value,key));
        actors.clear();
    }
    @Test void creationHasAtomicContactServerOwnershipTimersAndPublicMask() {
        long id=create(sales,"档案","13811112222");
        var detail=ok(call("GET","/api/customers/"+id,token(sales),null));
        assertThat(detail.path("ownerId").asLong()).isEqualTo(sales);
        assertThat(detail.path("teamId").asLong()).isEqualTo(east);
        assertThat(detail.path("customerStatus").asText()).isEqualTo("NEW");
        assertThat(detail.path("contacts").size()).isEqualTo(1);
        assertThat(detail.path("contacts").get(0).path("primary").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR,created_at,first_contact_deadline_at) FROM crm_customer WHERE id=?",Long.class,id)).isEqualTo(72);
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR,created_at,warning_at) FROM crm_customer WHERE id=?",Long.class,id)).isEqualTo(48);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=?",Long.class,id)).isEqualTo(1);
        long pub=create(admin,"公海","13822223333");
        var masked=ok(call("GET","/api/customers/"+pub,token(admin),null));
        assertThat(masked.path("primaryPhone").asText()).isEqualTo("138****3333");
        assertThat(masked.path("contacts").size()).isZero();
        assertThat(masked.toString()).doesNotContain("13822223333","unifiedSocialCreditCode");
        assertThat(jdbc.queryForObject("SELECT first_contact_deadline_at IS NULL FROM crm_customer WHERE id=?",Boolean.class,pub)).isTrue();
    }
    @Test void editsClassificationFiltersAndPaginationDoNotRefreshBusinessClocks() {
        long id=create(sales,"筛选","13811112222");
        var baseline=jdbc.queryForMap("SELECT created_at,first_contact_deadline_at,warning_at,last_effective_follow_at FROM crm_customer WHERE id=?",id);
        var profile=profile(prefix+"更新"); profile.put("region","上海"); profile.put("sourceCode","REFERRAL"); profile.put("industryCode","TECH");
        ok(call("PUT","/api/customers/"+id,token(sales),Map.of("version",0,"profile",profile)));
        ok(call("PATCH","/api/customers/"+id+"/classification",token(manager),Map.of("version",1,"customerLevel","KEY","tags",List.of("POTENTIAL","LOCAL"))));
        var result=ok(call("GET","/api/customers?size=10&customerLevel=KEY&sourceCode=REFERRAL&industryCode=TECH&tags=POTENTIAL,LOCAL&tagMatch=ALL",token(sales),null));
        assertThat(result.path("total").asLong()).isEqualTo(1);
        assertThat(result.path("list").get(0).path("id").asLong()).isEqualTo(id);
        assertThat(ok(call("GET","/api/customers?tags=LONG_TERM,LOCAL&tagMatch=ALL",token(sales),null)).path("total").asLong()).isZero();
        assertThat(ok(call("GET","/api/customers?tags=LONG_TERM,LOCAL&tagMatch=ANY",token(sales),null)).path("total").asLong()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT created_at,first_contact_deadline_at,warning_at,last_effective_follow_at FROM crm_customer WHERE id=?",id)).isEqualTo(baseline);
        code(call("PUT","/api/customers/"+id,token(sales),Map.of("version",0,"profile",profile)),409,40900);
        status(call("PATCH","/api/customers/"+id+"/classification",token(sales),Map.of("version",2,"customerLevel","KEY","tags",List.of("POTENTIAL","POTENTIAL"))),400);
    }
    @Test void exactDuplicatesAreBlockedAndSameNameRequiresPrivilegedReason() {
        long id=create(sales,"同名","13811112222");
        var same=payload(prefix+"同名","+86 138-1111-2222");
        code(call("POST","/api/customers",token(admin),same),409,40902);
        var review=payload(prefix+"同名","13899990000");
        code(call("POST","/api/customers",token(admin),review),409,40903);
        review.put("sameNameConfirmed",true); review.put("reviewReason","人工核对为不同主体"); review.put("ownerId",peer);
        status(call("POST","/api/customers",token(sales),review),403);
        long reviewed=ok(call("POST","/api/customers",token(admin),review)).asLong();
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,reviewed)).isEqualTo(peer);
        assertThat(jdbc.queryForObject("SELECT reason FROM crm_operation_log WHERE biz_id=? AND action_code='CUSTOMER_CREATED'",String.class,reviewed)).contains("人工核对为不同主体");
        assertThat(ok(call("GET","/api/customers/"+id,token(sales),null)).path("customerName").asText()).isEqualTo(prefix+"同名");
    }
    @Test void scopesAndHandoverReadOnlyCoverAllWriteEntrypoints() {
        long id=create(sales,"隔离","13811112222");
        status(call("GET","/api/customers/"+id,token(foreign),null),404);
        status(call("PUT","/api/customers/"+id,token(peer),Map.of("version",0,"profile",profile(prefix+"越权"))),404);
        jdbc.update("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,old_owner_id,ownership_seq,handover_readonly_until) VALUES('CUSTOMER',?,'HANDOVER',?,?,1,DATE_ADD(NOW(3),INTERVAL 7 DAY))",id,admin,foreign);
        String old=token(foreign);
        assertThat(ok(call("GET","/api/customers/"+id,old,null)).path("readonly").asBoolean()).isTrue();
        status(call("PUT","/api/customers/"+id,old,Map.of("version",0,"profile",profile(prefix+"越权"))),403);
        status(call("PATCH","/api/customers/"+id+"/classification",old,Map.of("version",0,"customerLevel","KEY","tags",List.of())),403);
        status(call("POST","/api/customers/"+id+"/contacts",old,Map.of("customerVersion",0,"contact",contact("越权","13833334444",false))),403);
        status(call("DELETE","/api/customers/"+id+"?version=0",old,null),403);
        var forged=payload(prefix+"伪造","13844445555");forged.put("ownerId",foreign);forged.put("seaType","PUBLIC");
        status(call("POST","/api/customers",token(manager),forged),403);
    }
    @Test void mainContactSwitchIsUniqueUpdatesPhoneAndRejectsReferencedDeletes() {
        long id=create(sales,"联系人","13811112222");String token=token(sales);
        long first=ok(call("GET","/api/customers/"+id,token,null)).path("contacts").get(0).path("id").asLong();
        ok(call("PUT","/api/customers/"+id+"/contacts/"+first,token,
                Map.of("customerVersion",0,"contact",contact("主要联系人已编辑","13822223333",true))));
        assertThat(ok(call("GET","/api/customers/"+id,token,null)).path("primaryPhone").asText()).isEqualTo("13822223333");
        long second=ok(call("POST","/api/customers/"+id+"/contacts",token,Map.of("customerVersion",1,"contact",contact("财务","13833334444",true)))).asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_contact WHERE customer_id=? AND is_primary=1",Long.class,id)).isEqualTo(1);
        assertThat(ok(call("GET","/api/customers/"+id,token,null)).path("primaryPhone").asText()).isEqualTo("13833334444");
        code(call("DELETE","/api/customers/"+id+"/contacts/"+second+"?customerVersion=2",token,null),400,40005);
        ok(call("DELETE","/api/customers/"+id+"/contacts/"+first+"?customerVersion=2",token,null));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_contact WHERE customer_id=?",Long.class,id)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO crm_contact(customer_id,contact_name,is_primary) VALUES(?,'重复主联系人',1)",id)).isInstanceOf(DataIntegrityViolationException.class);
        long other=create(peer,"其他客户","13855556666");
        status(call("PUT","/api/customers/"+other+"/contacts/"+second,token(manager),Map.of("customerVersion",0,"contact",contact("不能替换","13866667777",true))),404);
    }

    @Test void effectiveFollowIsTemporaryUntilReviewedAndReturnRecalculatesClock() {
        long id=create(sales,"跟进审核","13811112222");
        var detail=ok(call("GET","/api/customers/"+id,token(sales),null));
        long contactId=detail.path("contacts").get(0).path("id").asLong();
        String followedAt=java.time.LocalDateTime.now().minusMinutes(2).withNano(0).toString();
        String nextAt=java.time.LocalDateTime.now().plusDays(1).withNano(0).toString();
        var follow=Map.<String,Object>of("customerVersion",0,"contactId",contactId,"followMethod","PHONE","content","已核对项目需求和决策流程",
                "resultCode","NEED_CONFIRMED","substantiveResult","预算及上线时间已确认","nextAction","发送实施方案","nextActionAt",nextAt,"followedAt",followedAt);
        long followId=ok(call("POST","/api/customers/"+id+"/follow-records",token(sales),follow)).asLong();
        var pending=ok(call("GET","/api/customers/"+id,token(sales),null));
        assertThat(pending.path("followRecords").get(0).path("reviewStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.path("lastEffectiveFollowAt").asText()).isNotEmpty();
        status(call("PATCH","/api/customers/"+id+"/follow-records/"+followId+"/review",token(foreign),Map.of("version",1,"reviewStatus","CONFIRMED")),404);
        code(call("PATCH","/api/customers/"+id+"/follow-records/"+followId+"/review",token(manager),Map.of("version",1,"reviewStatus","RETURNED","reviewReason","缺少实际沟通结果依据")),200,0);
        var returned=ok(call("GET","/api/customers/"+id,token(sales),null));
        assertThat(returned.path("followRecords").get(0).path("effective").asBoolean()).isFalse();
        assertThat(returned.path("followRecords").get(0).path("reviewStatus").asText()).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("SELECT last_effective_follow_at IS NULL FROM crm_customer WHERE id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code IN ('FOLLOW_RECORDED','FOLLOW_REVIEWED')",Integer.class,id)).isEqualTo(2);
        status(call("DELETE","/api/customers/"+id+"/follow-records/"+followId,token(sales),null),404);
    }

    @Test void adminCanReviewAcrossTeamsButSubmitterCannotReviewOwnRecord() {
        long id=create(foreign,"跨团队跟进审核","13877778888");
        var detail=ok(call("GET","/api/customers/"+id,token(foreign),null));
        long contactId=detail.path("contacts").get(0).path("id").asLong();
        long followId=addEffectiveFollow(foreign,id,contactId,0,java.time.LocalDateTime.now().minusMinutes(3));

        status(call("PATCH","/api/customers/"+id+"/follow-records/"+followId+"/review",token(foreign),
                Map.of("version",1,"reviewStatus","CONFIRMED")),403);
        code(call("PATCH","/api/customers/"+id+"/follow-records/"+followId+"/review",token(admin),
                Map.of("version",1,"reviewStatus","CONFIRMED")),200,0);
        assertThat(jdbc.queryForObject("SELECT review_status FROM crm_follow_record WHERE id=?",String.class,followId)).isEqualTo("CONFIRMED");
    }

    @Test void returningNewestFollowRecalculatesFromRemainingEffectiveRecordsAndIneffectiveFollowDoesNotRefresh() {
        long id=create(sales,"多条跟进重算","13888889999");
        var detail=ok(call("GET","/api/customers/"+id,token(sales),null));
        long contactId=detail.path("contacts").get(0).path("id").asLong();
        var firstAt=java.time.LocalDateTime.now().minusMinutes(20).withNano(0);
        var secondAt=java.time.LocalDateTime.now().minusMinutes(10).withNano(0);
        long first=addEffectiveFollow(sales,id,contactId,0,firstAt);
        long second=addEffectiveFollow(sales,id,contactId,1,secondAt);
        var noResponse=followPayload(contactId,java.time.LocalDateTime.now().minusMinutes(1));
        noResponse.put("customerVersion",2); noResponse.put("resultCode","NO_RESPONSE");
        noResponse.put("substantiveResult","未接通");
        ok(call("POST","/api/customers/"+id+"/follow-records",token(sales),noResponse));
        assertThat(jdbc.queryForObject("SELECT last_effective_follow_at FROM crm_customer WHERE id=?",java.time.LocalDateTime.class,id))
                .isEqualTo(secondAt);

        code(call("PATCH","/api/customers/"+id+"/follow-records/"+second+"/review",token(manager),
                Map.of("version",2,"reviewStatus","RETURNED","reviewReason","未达到实质跟进标准")),200,0);
        assertThat(jdbc.queryForObject("SELECT last_effective_follow_at FROM crm_customer WHERE id=?",java.time.LocalDateTime.class,id))
                .isEqualTo(firstAt);
        assertThat(jdbc.queryForObject("SELECT is_effective FROM crm_follow_record WHERE id=?",Boolean.class,first)).isTrue();
    }

    private long addEffectiveFollow(long actor,long customerId,long contactId,long customerVersion,java.time.LocalDateTime followedAt) {
        var body=followPayload(contactId,followedAt);
        body.put("customerVersion",customerVersion);
        return ok(call("POST","/api/customers/"+customerId+"/follow-records",token(actor),body)).asLong();
    }

    private Map<String,Object> followPayload(long contactId,java.time.LocalDateTime followedAt) {
        return new HashMap<>(Map.of("contactId",contactId,"followMethod","PHONE","content","已核实采购计划和决策流程",
                "resultCode","NEED_CONFIRMED","substantiveResult","预算与采购时间已确认","nextAction","发送正式方案",
                "nextActionAt",java.time.LocalDateTime.now().plusDays(1).withNano(0).toString(),
                "followedAt",followedAt.withNano(0).toString()));
    }
    @Test void primaryContactDuplicateReviewIsRequiredOnlyWhenTheDuplicateKeyChanges() {
        long id=create(sales,"同名复核","13811112222");
        var duplicate=payload(prefix+"同名复核","13822223333");
        duplicate.put("ownerId",peer); duplicate.put("sameNameConfirmed",true); duplicate.put("reviewReason","已核实为不同经营主体");
        ok(call("POST","/api/customers",token(admin),duplicate));
        String salesToken=token(sales);
        var unchangedPhone=Map.of("customerVersion",0,"contact",contact("主要联系人调整","13811112222",true));
        ok(call("PUT","/api/customers/"+id+"/contacts/"+
                ok(call("GET","/api/customers/"+id,salesToken,null)).path("contacts").get(0).path("id").asLong(),
                salesToken,unchangedPhone));
        var change=Map.of("customerVersion",1,"contact",contact("主要联系人调整","13833334444",true));
        code(call("PUT","/api/customers/"+id+"/contacts/"+
                ok(call("GET","/api/customers/"+id,salesToken,null)).path("contacts").get(0).path("id").asLong(),
                salesToken,change),409,40903);
        var reviewed=new LinkedHashMap<String,Object>(change);
        reviewed.put("sameNameConfirmed",true); reviewed.put("reviewReason","电话核对后确认客户主体不同");
        ok(call("PUT","/api/customers/"+id+"/contacts/"+
                ok(call("GET","/api/customers/"+id,token(manager),null)).path("contacts").get(0).path("id").asLong(),
                token(manager),reviewed));
        assertThat(ok(call("GET","/api/customers/"+id,salesToken,null)).path("primaryPhone").asText()).isEqualTo("13833334444");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='CONTACT_UPDATED' AND reason LIKE '%电话核对后确认%'",Long.class,id)).isEqualTo(1);
    }
    @Test void logicalDeleteIsAdminOnlyKeepsHistoryAndReleasesActiveIdentity() {
        long id=create(sales,"删除","13811112222");
        status(call("DELETE","/api/customers/"+id+"?version=0",token(sales),null),403);
        ok(call("DELETE","/api/customers/"+id+"?version=0",token(admin),null));
        status(call("GET","/api/customers/"+id,token(admin),null),404);
        assertThat(ok(call("GET","/api/customers",token(sales),null)).path("total").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT deleted FROM crm_customer WHERE id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_contact WHERE customer_id=?",Long.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_id=? AND action_code='CUSTOMER_DELETED'",Long.class,id)).isEqualTo(1);
        long recreated=ok(call("POST","/api/customers",token(admin),payload(prefix+"删除","13811112222"))).asLong();
        assertThat(recreated).isNotEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE customer_name=?",Long.class,prefix+"删除")).isEqualTo(2);
    }
    @Test void businessHistoryPreventsDeletion() {
        long id=create(sales,"保留历史","13811112222");
        jdbc.update("INSERT INTO crm_follow_record(customer_id,follow_user_id,record_type,content) VALUES(?,?,'FOLLOW','历史记录')",id,sales);
        code(call("DELETE","/api/customers/"+id+"?version=0",token(admin),null),409,40904);
        assertThat(jdbc.queryForObject("SELECT deleted FROM crm_customer WHERE id=?",Boolean.class,id)).isFalse();
    }
    @Test void dictionaryManagementIsAdminOnlyVersionedAndDisabledCodesRemainReadable() {
        String code=prefix.toUpperCase(Locale.ROOT).replace('-','_');
        var request=Map.of("group","tag","code",code,"label","验证标签","sortNo",99);
        status(call("POST","/api/customer-dictionaries",token(manager),request),403);
        ok(call("POST","/api/customer-dictionaries",token(admin),request));
        Long config=jdbc.queryForObject("SELECT id FROM sys_config WHERE config_group='customer.tag' AND config_key=?",Long.class,code);
        var body=payload(prefix+"标签","13811112222");var profile=profile(prefix+"标签"); profile.put("tags",List.of(code));body.put("profile",profile);body.put("ownerId",sales);
        long id=ok(call("POST","/api/customers",token(admin),body)).asLong();
        ok(call("PUT","/api/customer-dictionaries/"+config,token(admin),Map.of("version",0,"label","已停用标签","sortNo",99,"enabled",false)));
        assertThat(ok(call("GET","/api/customers/"+id,token(sales),null)).path("tags").get(0).asText()).isEqualTo(code);
        ok(call("PUT","/api/customers/"+id,token(sales),Map.of("version",0,"profile",profile)));
        body.put("profile",new LinkedHashMap<>(profile)); ((Map<String,Object>)body.get("profile")).put("customerName",prefix+"新标签");
        status(call("POST","/api/customers",token(admin),body),400);
        code(call("PUT","/api/customer-dictionaries/"+config,token(admin),Map.of("version",0,"label","旧版本","sortNo",99,"enabled",true)),409,40900);
    }
    @Test void duplicateConcurrentCreatesCommitOnlyOneAndAuditFailureRollsBack() throws Exception {
        var payload=payload(prefix+"并发","13811112222"); String one=token(admin),two=token(admin);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> {start.await();return call("POST","/api/customers",one,payload).getStatusCode().value();});
            var b=pool.submit(() -> {start.await();return call("POST","/api/customers",two,payload).getStatusCode().value();});
            start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE customer_name=?",Long.class,prefix+"并发")).isEqualTo(1);
        doThrow(new DataIntegrityViolationException("Injected audit failure")).when(customers)
                .audit(anyLong(),eq("CUSTOMER_CREATED"),anyLong(),any(),any(),anyLong(),any(),anyString(),anyString());
        status(call("POST","/api/customers",token(admin),payload(prefix+"回滚","13899990000")),409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE customer_name=?",Long.class,prefix+"回滚")).isZero();
    }
    @Test void validationAndUnauthenticatedRequestsAreRejected() {
        status(call("POST","/api/customers",null,payload(prefix+"匿名","13811112222")),401);
        status(call("POST","/api/customers",token(sales),Map.of()),400);
        status(call("GET","/api/customers?tagMatch=INJECT",token(sales),null),400);
        status(call("GET","/api/customers?page=0",token(sales),null),400);
        var body=payload(prefix+"电话","----");status(call("POST","/api/customers",token(admin),body),400);
        assertThat(ok(call("GET","/api/customers/owners",token(manager),null)).size()).isEqualTo(3);
    }

    @Test void publicClaimIsAtomicAndEnforcesDailyLimit() throws Exception {
        long publicId=create(admin,"并发领取","13855550001");
        long version=ok(call("GET","/api/customers/"+publicId,token(sales),null)).path("version").asLong();
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> {start.await();return call("POST","/api/customers/"+publicId+"/claim?version="+version,token(sales),null).getStatusCode().value();});
            var b=pool.submit(() -> {start.await();return call("POST","/api/customers/"+publicId+"/claim?version="+version,token(peer),null).getStatusCode().value();});
            start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,404);
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='PUBLIC_CLAIM'",Long.class,publicId)).isEqualTo(1);
        Long winner=jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,publicId);
        assertThat(winner).isIn(sales,peer);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,winner)).isEqualTo(1);

        jdbc.update("UPDATE sys_config SET config_value='1' WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='claim_daily_limit'");
        long second=create(admin,"达到日限","13855550002");
        jdbc.update("UPDATE sys_user SET claim_date=CURDATE(),claim_count=1 WHERE id=?",sales);
        code(call("POST","/api/customers/"+second+"/claim?version=0",token(sales),null),400,40007);
        assertThat(jdbc.queryForObject("SELECT sea_type FROM crm_customer WHERE id=?",String.class,second)).isEqualTo("PUBLIC");
    }

    @Test void thirdDailyClaimSucceedsButConcurrentFourthCannotChangeOwnershipOrCounters() throws Exception {
        jdbc.update("UPDATE sys_config SET config_value='3' WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key='claim_daily_limit'");
        long first=create(admin,"日限第三次","13855550101"), second=create(admin,"日限第四次","13855550102");
        jdbc.update("UPDATE sys_user SET claim_date=CURDATE(),claim_count=2 WHERE id=?",sales);
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            var a=pool.submit(() -> {start.await(); return call("POST","/api/customers/"+first+"/claim?version=0",token(sales),null);});
            var b=pool.submit(() -> {start.await(); return call("POST","/api/customers/"+second+"/claim?version=0",token(sales),null);});
            start.countDown();
            var responses=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertThat(responses.stream().map(r -> r.getStatusCode().value()).toList()).containsExactlyInAnyOrder(200,400);
            responses.stream().filter(r -> r.getStatusCode().value()==400).forEach(r -> code(r,400,40007));
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT claim_count FROM sys_user WHERE id=?",Integer.class,sales)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,sales)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE id IN (?,?) AND owner_id=? AND sea_type='PRIVATE'",Integer.class,first,second,sales)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE id IN (?,?) AND owner_id IS NULL AND sea_type='PUBLIC' AND version=0",Integer.class,first,second)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id IN (?,?) AND action_code='PUBLIC_CLAIM'",Integer.class,first,second)).isEqualTo(1);
    }

    @Test void priorOwnerPriorityClaimExemptsLimitsAndTransferIsAuditedAndReadOnly() {
        jdbc.update("UPDATE sys_config SET config_value='1' WHERE config_type='PARAM' AND config_group='crm.customer' AND config_key IN ('claim_hold_limit','claim_daily_limit')");
        long held=create(sales,"已达持有上限","13855550003");
        long returned=create(admin,"优先重领","13855550004");
        jdbc.update("UPDATE crm_customer SET previous_owner_id=?,return_priority_until=DATE_ADD(NOW(3),INTERVAL 7 DAY) WHERE id=?",sales,returned);
        jdbc.update("UPDATE sys_user SET claim_date=CURDATE(),claim_count=1 WHERE id=?",sales);
        ok(call("POST","/api/customers/"+returned+"/claim?version=0",token(sales),null));
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,returned)).isEqualTo(sales);
        assertThat(jdbc.queryForObject("SELECT claim_count FROM sys_user WHERE id=?",Integer.class,sales)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT action_code FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='PRIORITY_CLAIM'",String.class,returned)).isEqualTo("PRIORITY_CLAIM");

        long transferId=create(sales,"管理员转交","13855550005");
        var transferBody=Map.of("version",0,"newOwnerId",peer,"reason","团队内工作调整");
        status(call("PATCH","/api/customers/"+transferId+"/ownership",token(manager),transferBody),403);
        ok(call("PATCH","/api/customers/"+transferId+"/ownership",token(admin),transferBody));
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,transferId)).isEqualTo(peer);
        assertThat(jdbc.queryForObject("SELECT TIMESTAMPDIFF(DAY,occurred_at,handover_readonly_until) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='OWNER_TRANSFERRED'",Long.class,transferId)).isEqualTo(7);
        var oldView=ok(call("GET","/api/customers/"+transferId,token(sales),null));
        assertThat(oldView.path("readonly").asBoolean()).isTrue();
        status(call("PUT","/api/customers/"+transferId,token(sales),Map.of("version",1,"profile",profile(prefix+"旧负责人编辑"))),403);
        ok(call("GET","/api/customers/"+transferId,token(peer),null));
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,sales)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,peer)).isEqualTo(1);
    }

    @Test void ownershipAuditFailureRollsBackTransfer() {
        long id=create(sales,"转交事务回滚","13855550006");
        doThrow(new DataIntegrityViolationException("Injected ownership audit failure")).when(customers)
                .ownershipAudit(eq(id),eq("OWNER_TRANSFERRED"),anyLong(),any(),any(),anyLong(),any(),any(),any(),any(),anyString(),anyString(),anyString());
        status(call("PATCH","/api/customers/"+id+"/ownership",token(admin),Map.of("version",0,"newOwnerId",peer,"reason","验证回滚")),409);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM crm_customer WHERE id=?",Long.class,id)).isEqualTo(sales);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE biz_type='CUSTOMER' AND biz_id=? AND action_code='OWNER_TRANSFERRED'",Long.class,id)).isZero();
    }
    @Test void onlyAdminCreatesAndAssignsActiveManagersOrSpecialists() {
        var body=payload(prefix+"分配","13855557777"); body.put("ownerId",sales);
        for (Long actor:List.of(sales,manager,peer,foreign)) status(call("POST","/api/customers",token(actor),body),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_customer WHERE created_by IN (?,?,?,?,?)",Long.class,admin,manager,sales,peer,foreign)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crm_operation_log WHERE operator_id IN (?,?,?,?,?)",Long.class,admin,manager,sales,peer,foreign)).isZero();
        long managerCustomer=create(manager,"经理负责","13855557771"), salesCustomer=create(sales,"专员负责","13855557772");
        for (long id:List.of(managerCustomer,salesCustomer)) {
            var row=ok(call("GET","/api/customers/"+id,token(admin),null));
            long owner=id==managerCustomer?manager:sales;
            assertThat(row.path("ownerId").asLong()).isEqualTo(owner);
            assertThat(row.path("teamId").asLong()).isEqualTo(east);
            assertThat(row.path("seaType").asText()).isEqualTo("PRIVATE");
            assertThat(jdbc.queryForObject("SELECT created_by FROM crm_customer WHERE id=?",Long.class,id)).isEqualTo(admin);
            assertThat(jdbc.queryForObject("SELECT current_hold_count FROM sys_user WHERE id=?",Integer.class,owner)).isEqualTo(1);
        }
        body.put("ownerId",admin); status(call("POST","/api/customers",token(admin),body),403);
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?",peer);
        body.put("ownerId",peer); status(call("POST","/api/customers",token(admin),body),403);
        jdbc.update("UPDATE sys_team SET enabled=0 WHERE id=?",south);
        body.put("ownerId",foreign); status(call("POST","/api/customers",token(admin),body),403);
    }

    @Test void customerPagesAreFixedAtTenAndCreationPickerFiltersBeforePagination() {
        for (int i=0;i<12;i++) create(sales,"分页"+i,"1385555"+String.format("%04d",i));
        var first=ok(call("GET","/api/customers?page=1",token(sales),null));
        var second=ok(call("GET","/api/customers?page=2",token(sales),null));
        assertThat(first.path("total").asInt()).isEqualTo(12);
        assertThat(first.path("list").size()).isEqualTo(10); assertThat(second.path("list").size()).isEqualTo(2);
        Set<Long> ids=new HashSet<>();first.path("list").forEach(x->ids.add(x.path("id").asLong()));second.path("list").forEach(x->ids.add(x.path("id").asLong()));
        assertThat(ids).hasSize(12);
        status(call("GET","/api/customers?size=20",token(admin),null),400);
        jdbc.update("UPDATE crm_customer SET customer_status='FOLLOWING' WHERE owner_id=?",sales);
        long fresh=create(sales,"未首联","13855559999");
        var eligible=ok(call("GET","/api/customers?opportunityEligible=true&ownerId="+sales+"&page=2",token(admin),null));
        assertThat(eligible.path("total").asInt()).isEqualTo(12);assertThat(eligible.path("list").size()).isEqualTo(2);
        eligible.path("list").forEach(row->assertThat(row.path("id").asLong()).isNotEqualTo(fresh));
        assertThat(ok(call("GET","/api/customers?opportunityEligible=true&ownerId="+sales,token(foreign),null)).path("total").asInt()).isZero();
    }

    long create(Long owner,String name,String phone) { var body=payload(prefix+name,phone); if (!owner.equals(admin)) body.put("ownerId",owner); return ok(call("POST","/api/customers",token(admin),body)).asLong();}
    Map<String,Object> profile(String name) {var p=new LinkedHashMap<String,Object>();p.put("customerName",name);p.put("customerLevel","NORMAL");p.put("tags",List.of());return p;}
    Map<String,Object> contact(String name,String phone,boolean primary) {return new LinkedHashMap<>(Map.of("contactName",name,"phone",phone,"primary",primary,"decisionRole","CONTACT"));}
    Map<String,Object> payload(String name,String phone) {return new LinkedHashMap<>(Map.of("profile",profile(name),"primaryContact",contact("主要联系人",phone,true)));}
    Long team(String suffix) {jdbc.update("INSERT INTO sys_team(team_code,team_name) VALUES(?,?)",prefix+suffix,prefix+suffix);return jdbc.queryForObject("SELECT id FROM sys_team WHERE team_code=?",Long.class,prefix+suffix);}
    Long user(String suffix,String role,Long team) {jdbc.update("INSERT INTO sys_user(username,password_hash,real_name,role_code,team_id,must_change_password) VALUES(?,?,?,?,?,0)",prefix+suffix,passwords.encode("CustomerFixture123!"),suffix,role,team);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,prefix+suffix);actors.add(id);return id;}
    String token(Long actor) {return tokens.issue(AuthPrincipal.from(users.findWithTeamById(actor))).value();}
    ResponseEntity<JsonNode> call(String method,String url,String token,Object body) {var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(token!=null)headers.setBearerAuth(token);return http.exchange(url,HttpMethod.valueOf(method),new HttpEntity<>(body,headers),JsonNode.class);}
    JsonNode ok(ResponseEntity<JsonNode> response) {status(response,200);assertThat(response.getBody().path("code").asInt()).isZero();return response.getBody().path("data");}
    void status(ResponseEntity<JsonNode> response,int expected) {assertThat(response.getStatusCode().value()).withFailMessage("%s",response.getBody()).isEqualTo(expected);}
    void code(ResponseEntity<JsonNode> response,int status,int code) {status(response,status);assertThat(response.getBody().path("code").asInt()).isEqualTo(code);}
}
