/* 文件作用：看板授权、日期口径、补零月份、转化率和缓存组合。 */
package com.bishe.crm.module.analytics.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.analytics.dto.DashboardQuery;
import com.bishe.crm.module.analytics.mapper.DashboardMapper;
import com.bishe.crm.module.analytics.vo.DashboardViews.*;
import com.bishe.crm.module.auth.security.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class DashboardService {
    private final DashboardMapper mapper;
    private final DashboardCache cache;
    private final Clock clock;
    /** 选择项只来自服务端允许的当前及历史业绩人员。 */
    public List<Owner> owners(AuthPrincipal actor) {usable(actor);return mapper.owners(DataScope.of(actor));}
    /** 当前指标与历史期间指标在同一只读事务内聚合，本人待办始终实时。 */
    @Transactional(readOnly=true)
    public Result overview(AuthPrincipal actor,DashboardQuery query) {
        usable(actor);LocalDate today=LocalDate.now(clock);
        LocalDate from=query.getFrom()==null?today.withDayOfMonth(1):query.getFrom();
        LocalDate to=query.getTo()==null?today:query.getTo();
        if(to.isBefore(from)||to.isAfter(today)||ChronoUnit.DAYS.between(from,to)>1095||from.isBefore(LocalDate.of(2000,1,1))) throw new BizException(ErrorCode.BAD_REQUEST);
        Long owner=query.getOwnerId();
        if(owner!=null&&owners(actor).stream().noneMatch(x->owner.equals(x.id()))) throw new BizException(ErrorCode.FORBIDDEN);
        DataScope scope=DataScope.of(actor);
        String identity=actor.id()+":"+actor.roleCode()+":"+actor.teamId()+":"+actor.version()+":"+from+":"+to+":"+owner+":"+today;
        String key=cache.key(identity);
        Result result=query.isRefresh()?null:cache.get(key);
        String source="数据库";
        if(result==null) {
            LocalDateTime start=from.atStartOfDay(),end=to.plusDays(1).atStartOfDay();
            var m=mapper.metrics(scope,owner,start,end,today);
            BigDecimal contractTotal=money(m,"currentContractAmount"),contractReceived=money(m,"currentReceivedAmount");
            var metrics=new Metrics(n(m,"customerCount"),n(m,"openOpportunities"),money(m,"signedAmount"),money(m,"receivedAmount"),money(m,"outstandingAmount"),money(m,"overdueAmount"),contractTotal,contractReceived,contractTotal.signum()==0?null:contractReceived.multiply(BigDecimal.valueOf(100)).divide(contractTotal,2,RoundingMode.HALF_UP));
            var stages=new ArrayList<Stage>();var stageRows=mapper.stages(scope,owner,start,end);
            String[][] names={{"NEW","线索"},{"FOLLOWING","跟进"},{"QUOTED","报价"},{"DEAL","成交"},{"WON","赢单"},{"LOST","输单"},{"CLOSED","关闭"}};
            for(var pair:names) {var row=stageRows.stream().filter(r->pair[0].equals(r.get("code"))).findFirst().orElse(Map.of());stages.add(new Stage(pair[0],pair[1],n(row,"count"),money(row,"amount")));}
            var trendRows=mapper.trends(scope,owner,start,end);var trends=new ArrayList<Trend>();
            for(YearMonth month=YearMonth.from(from);!month.isAfter(YearMonth.from(to));month=month.plusMonths(1)) {
                String label=month.toString();var row=trendRows.stream().filter(r->label.equals(r.get("month"))).findFirst().orElse(Map.of());
                trends.add(new Trend(label,money(row,"signedAmount"),money(row,"receivedAmount")));
            }
            var c=mapper.conversion(scope,owner,start,end);long deal=n(c,"dealCount"),won=n(c,"wonCount");
            var conversion=new Conversion(n(c,"cohortCount"),deal,won,deal==0?null:BigDecimal.valueOf(won).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(deal),2,RoundingMode.HALF_UP),c.get("averageSigningDays")==null?null:money(c,"averageSigningDays"));
            String[][] stageNames={{"NEW","线索"},{"FOLLOWING","跟进"},{"QUOTED","报价"},{"DEAL","成交"}};
            var durationRows=mapper.stageDurations(scope,owner,LocalDateTime.now(clock));var durations=new ArrayList<StageDuration>();
            for(var pair:stageNames){var row=durationRows.stream().filter(r->pair[0].equals(r.get("code"))).findFirst().orElse(Map.of());durations.add(new StageDuration(pair[0],pair[1],n(row,"opportunityCount"),row.get("averageDays")==null?BigDecimal.ZERO.setScale(2):money(row,"averageDays")));}
            var quality=new ProcessQuality(durations,mapper.rollbackCount(scope,owner,start,end));
            // 赢单数按首次赢单事件去重，不把合同份数误称为赢单商机数。
            var ranks=mapper.ranking(scope,owner,start,end).stream().map(r->new Rank(n(r,"ownerId"),Objects.toString(r.get("ownerName"),"历史账号"),Objects.toString(r.get("teamName"),"未分配团队"),n(r,"signedCount"),money(r,"signedAmount"),money(r,"receivedAmount"),n(r,"wonCount"))).toList();
            result=new Result(from,to,"ADMIN".equals(actor.roleCode())?"全部业务":"MANAGER".equals(actor.roleCode())?"本团队业务":"本人业务",LocalDateTime.now(clock),source,metrics,stages,trends,ranks,conversion,List.of(),n(m,"legacyContracts"),n(m,"legacyReceipts"),quality);
            if(key.equals(cache.key(identity)))cache.put(key,result);
        } else source="Redis缓存";
        var actions=mapper.actions(actor.id()).stream().map(r->{String type=Objects.toString(r.get("type"));String title=switch(type){case "CONTRACT_REVIEW"->"合同待核验";case "CARE_BIRTHDAY"->"联系人生日关怀";case "FOLLOW_DUE"->"下次跟进提醒";case "CONTRACT_STAGNANT"->"合同办理停滞";case "CUSTOMER_WARNING"->"客户回收预警";case "APPROVAL"->"待审核申请";case "PAYMENT_OVERDUE"->"逾期回款提醒";case "OPP_STAGNANT"->"商机停滞提醒";case "OPP_CLOSE_PENDING"->"商机待裁决";case "OPP_ESCALATED"->"商机裁决超时";default->"其他待处理事项";};return new Action(type,title,n(r,"count"),"/tasks?type="+type);}).toList();
        return new Result(result.from(),result.to(),result.scope(),result.generatedAt(),source,result.metrics(),result.stages(),result.trends(),result.ranking(),result.conversion(),actions,result.legacyContracts(),result.legacyReceipts(),result.processQuality());
    }
    /** 聚合空值统一补零，金额仅用十进制数。 */
    private BigDecimal money(Map<String,Object> row,String key) {Object v=row.get(key);return v==null?new BigDecimal("0.00"):new BigDecimal(v.toString()).setScale(2,RoundingMode.HALF_UP);}
    /** 数据库计数统一转long。 */
    private long n(Map<String,Object> row,String key) {Object v=row.get(key);return v==null?0:((Number)v).longValue();}
    /** 保留首次改密限制，不允许未知角色退化为全量范围。 */
    private void usable(AuthPrincipal actor) {if(actor==null)throw new BizException(ErrorCode.UNAUTHORIZED);if(actor.mustChangePassword())throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);if(!Set.of("ADMIN","MANAGER","SPECIALIST").contains(actor.roleCode()))throw new BizException(ErrorCode.FORBIDDEN);}
}
