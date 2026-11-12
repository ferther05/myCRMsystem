/* 文件作用：按三角色工作重点整合实时业务工作项，不在首页重复实现审批。 */
package com.bishe.crm.module.workbench.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.analytics.mapper.DashboardMapper;
import com.bishe.crm.module.workbench.dto.WorkbenchQuery;
import com.bishe.crm.module.workbench.mapper.WorkbenchMapper;
import com.bishe.crm.module.workbench.vo.WorkbenchViews.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkbenchService {
    private final WorkbenchMapper mapper;
    private final DashboardMapper dashboard;
    private final Clock clock;
    private static final Map<String,String> TITLES=Map.ofEntries(
        Map.entry("TODAY","今天要处理"),Map.entry("FOLLOW_DUE","到期跟进"),Map.entry("CUSTOMER_WARNING","客户预警"),
        Map.entry("RETURNED","待补充资料"),Map.entry("OPEN","在办商机"),Map.entry("REVIEW","待我核验"),
        Map.entry("FOLLOW_REVIEW","待核验跟进"),Map.entry("CONTRACT_REVIEW","待核验合同资料"),
        Map.entry("APPROVAL","业务审批"),Map.entry("STAGNANT","停滞商机"),Map.entry("OVERDUE","逾期回款期次"),
        Map.entry("JOB_FAILURE","扫描异常"),Map.entry("ACCOUNTS","账号申请审核"));

    /** 分类由当前身份确定，客户端不能提供人员或团队扩大范围。 */
    private List<String> categories(AuthPrincipal a) {
        if(a==null) throw new BizException(ErrorCode.UNAUTHORIZED);
        if(a.mustChangePassword()) throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        return switch(a.roleCode()) {
            case "SPECIALIST" -> List.of("TODAY","FOLLOW_DUE","CUSTOMER_WARNING","RETURNED","OPEN");
            case "MANAGER" -> List.of("REVIEW","APPROVAL","STAGNANT","OVERDUE","FOLLOW_REVIEW","CONTRACT_REVIEW");
            case "ADMIN" -> List.of("JOB_FAILURE","ACCOUNTS","OVERDUE","REVIEW","APPROVAL");
            default -> throw new BizException(ErrorCode.FORBIDDEN);
        };
    }

    /** 一个只读事务内形成完整授权计数；经营金额复用既有轻量统计SQL。 */
    @Transactional(readOnly=true)
    public Overview overview(AuthPrincipal a) {
        var allowed=categories(a);var scope=DataScope.of(a);var now=LocalDateTime.now(clock);
        var counts=new LinkedHashMap<String,Long>();
        for(String key:allowed) counts.put(key,mapper.count(scope,key,now.toLocalDate()));
        List<Metric> metrics=new ArrayList<>();
        if("ADMIN".equals(a.roleCode())) {
            var values=dashboard.metrics(scope,null,now.toLocalDate().withDayOfMonth(1).atStartOfDay(),now.toLocalDate().plusDays(1).atStartOfDay(),now.toLocalDate());
            metrics.add(new Metric("signedAmount","本月核验签约",number(values.get("signedAmount")),"元","按首次文件核验日期统计",null));
            metrics.add(new Metric("receivedAmount","本月实际到账",number(values.get("receivedAmount")),"元","按实际到账日期，不含作废记录",null));
            metrics.add(new Metric("outstandingAmount","当前未收金额",number(values.get("outstandingAmount")),"元","当前已签合同余额，含历史合同",null));
            metrics.add(new Metric("overdueAmount","当前逾期金额",number(values.get("overdueAmount")),"元","当前有效或暂停计划的到期未收","OVERDUE"));
        } else {
            var keys="MANAGER".equals(a.roleCode())?List.of("FOLLOW_REVIEW","CONTRACT_REVIEW","STAGNANT","OVERDUE"):List.of("FOLLOW_DUE","CUSTOMER_WARNING","RETURNED","OPEN");
            for(String key:keys) metrics.add(new Metric(key,TITLES.get(key),BigDecimal.valueOf(counts.get(key)),"项",
                key.equals("FOLLOW_REVIEW")?"本团队当前阶段/轮次，排除本人提交":key.equals("CONTRACT_REVIEW")?"登记/最新文件待核验，排除本人负责":key.equals("OVERDUE")?"按逾期未收期次计数":"当前授权范围的业务工作项",key));
        }
        return new Overview(a.roleCode(),"ADMIN".equals(a.roleCode())?"全局业务":"MANAGER".equals(a.roleCode())?a.teamName()+" · 本团队业务":"本人及授权商机",now,metrics,
            allowed.stream().map(k->new Category(k,TITLES.get(k),counts.get(k))).toList());
    }

    /** 分页只提供定位，打开详情与实际提交仍由原业务接口重新授权。 */
    @Transactional(readOnly=true)
    public PageResult<Item> items(AuthPrincipal a,WorkbenchQuery q) {
        if(!categories(a).contains(q.getCategory())) throw new BizException(ErrorCode.FORBIDDEN);
        var page=q.<Map<String,Object>>toPage();page.setOptimizeCountSql(false);
        var rows=mapper.items(page,DataScope.of(a),q.getCategory(),LocalDate.now(clock));
        return new PageResult<>(rows.getTotal(),rows.getRecords().stream().map(this::item).toList());
    }

    /** 所有路径由服务器固定模板和数据库数字主键构建。 */
    private Item item(Map<String,Object> r) {
        String type=Objects.toString(r.get("type"),"");long id=((Number)r.get("bizId")).longValue();
        String section=Objects.toString(r.get("section"),"");
        String target=switch(Objects.toString(r.get("bizType"),"")) {
            case "CUSTOMER" -> "/customers?id="+id+"&section="+section;
            case "OPPORTUNITY" -> "/opportunities?id="+id+"&section="+section+(r.get("relatedId")==null?"":"&followRecordId="+((Number)r.get("relatedId")).longValue());
            case "CONTRACT" -> "/opportunities?view=contracts&contractId="+id+"&section="+section;
            case "USER" -> "/approvals";
            case "JOB" -> "/tasks?tab=jobs";
            default -> "/tasks";
        };
        Object time=r.get("occurredAt");
        return new Item(Objects.toString(r.get("itemKey")),type,Objects.toString(r.get("title")),Objects.toString(r.get("objectName"),""),
            Objects.toString(r.get("actorName"),""),time instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)time,
            r.get("amount")==null?null:number(r.get("amount")),target);
    }
    /** 金额保留数据库精度，计数统一转换为展示数值。 */
    private BigDecimal number(Object value) { return value==null?BigDecimal.ZERO:new BigDecimal(value.toString()); }
}
