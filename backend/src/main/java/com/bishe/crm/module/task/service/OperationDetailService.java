/* 文件作用：将已授权日志快照转换为字段前后对比，不向浏览器暴露原始JSON。 */
package com.bishe.crm.module.task.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationDetailService {
    private final TaskMapper mapper;
    private final ObjectMapper json;
    private static final Map<String,String> FIELDS=Map.ofEntries(
        Map.entry("customerStatus","客户状态"),Map.entry("lastEffectiveFollowAt","最近有效跟进时间"),Map.entry("ourContactName","我方联系人"),Map.entry("counterpartyContactName","对方联系人"),Map.entry("ourContactPhone","我方电话（脱敏）"),Map.entry("counterpartyContactPhone","对方电话（脱敏）"),Map.entry("description","业务内容（敏感信息脱敏）"),Map.entry("paymentTerms","付款约定（敏感信息脱敏）"),Map.entry("deliveryTerms","交付约定（敏感信息脱敏）"),Map.entry("total","计划总额"),Map.entry("customerName","客户名称"),Map.entry("customerLevel","客户等级"),Map.entry("tags","标签"),
        Map.entry("sourceCode","来源"),Map.entry("industryCode","行业"),Map.entry("region","地区"),Map.entry("companySize","企业规模"),
        Map.entry("primaryPhone","主要电话（脱敏）"),Map.entry("contactName","联系人"),Map.entry("jobTitle","职务"),Map.entry("phone","电话（脱敏）"),Map.entry("email","邮箱（脱敏）"),Map.entry("decisionRole","联系人角色"),Map.entry("primary","主要联系人"),
        Map.entry("ownerId","负责人"),Map.entry("teamId","所属团队"),Map.entry("ownershipSeq","归属轮次"),
        Map.entry("stageCode","商机阶段"),Map.entry("opportunityName","商机名称"),Map.entry("expectedAmount","预计金额"),Map.entry("expectedCloseDate","预计成交日期"),Map.entry("stagnationDays","停滞天数"),Map.entry("graceDays","裁决宽限天数"),Map.entry("activityStatus","活跃状态"),Map.entry("quoteAmount","报价金额"),Map.entry("quoteAt","报价时间"),Map.entry("customerConfirmResult","合作确认结果"),Map.entry("customerConfirmAt","合作确认时间"),
        Map.entry("contractNo","内部登记号"),Map.entry("externalContractNo","正式合同编号"),Map.entry("contractName","合同名称"),Map.entry("businessType","业务类型"),Map.entry("ourPartyName","我方主体"),Map.entry("counterpartyName","对方主体"),Map.entry("plannedSignedAt","预计签订日期"),Map.entry("signedAt","实际签订日期"),Map.entry("expireDate","履行截止日期"),Map.entry("status","状态"),Map.entry("signingProgress","签署进度"),Map.entry("resumeSigningProgress","暂停前进度"),Map.entry("amount","金额"),Map.entry("receivedAmount","累计到账"),Map.entry("outstandingAmount","未收金额"),Map.entry("paymentStatus","回款状态"),Map.entry("reviewStatus","查验状态"),Map.entry("isEffective","是否有效"),Map.entry("accountStatus","账号状态"),Map.entry("roleCode","角色"),Map.entry("realName","姓名"));
    public record Change(String field,String before,String after) {}
    public record Detail(Long id,List<Change> changes,String note) {}

    /** 对象级授权后只输出白名单字段，并再次脱敏历史快照中的联系方式。 */
    @Transactional(readOnly=true)
    public Detail detail(AuthPrincipal actor,Long id) {
        if(actor==null||actor.mustChangePassword())throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        var row=mapper.operationSnapshots(id,DataScope.of(actor));
        if(row==null)throw new BizException(ErrorCode.NOT_FOUND);
        var before=flatten(parse(row.get("before_json")));var after=flatten(parse(row.get("after_json")));
        Set<String> keys=new LinkedHashSet<>(before.keySet());keys.addAll(after.keySet());
        List<Change> result=new ArrayList<>();
        for(String key:keys) {
            String field=key.contains(".")?key.substring(key.indexOf('.')+1):key;
            if(!FIELDS.containsKey(field)||Objects.equals(before.get(key),after.get(key)))continue;
            String prefix="";
            if(key.contains(".")) {
                String nameKey=key.substring(0,key.indexOf('.'))+".contactName";
                JsonNode name=after.containsKey(nameKey)?after.get(nameKey):before.get(nameKey);
                prefix="联系人"+(name==null?"": "「"+name.asText()+"」")+" · ";
            }
            result.add(new Change(prefix+FIELDS.get(field),value(field,before.get(key)),value(field,after.get(key))));
        }
        return new Detail(id,result,result.isEmpty()?"该记录没有可展示的字段差异；历史缺失的快照不会补造，请结合操作说明查询。":"仅展示已保存且允许查询的字段，联系方式始终脱敏。");
    }
    /** 旧日志无快照或格式异常时保留摘要，不把解析异常变成敏感原文。 */
    private JsonNode parse(Object raw) {try{return raw==null?json.nullNode():json.readTree(raw.toString());}catch(Exception e){return json.nullNode();}}
    private Map<String,JsonNode> flatten(JsonNode node) {
        Map<String,JsonNode> out=new LinkedHashMap<>();
        if(node.isObject())node.properties().forEach(e->out.put(e.getKey(),e.getValue()));
        if(node.isArray())for(int i=0;i<node.size();i++){final String prefix=node.get(i).path("id").asText(String.valueOf(i+1));node.get(i).properties().forEach(e->out.put(prefix+"."+e.getKey(),e.getValue()));}
        return out;
    }
    /** 用户和团队用名称；敏感字段不允许返回原值，日期截到秒。 */
    private String value(String key,JsonNode n) {
        if(n==null||n.isNull())return "—";
        if(("ownerId".equals(key)||"teamId".equals(key))&&n.canConvertToLong())return Objects.toString("ownerId".equals(key)?mapper.userName(n.asLong()):mapper.teamName(n.asLong()),"历史对象已不存在");
        if("tags".equals(key)&&n.isArray()) {
            List<String> labels=new ArrayList<>();for(JsonNode tag:n)labels.add(Objects.toString(mapper.dictionaryLabel("customer.tag",tag.asText()),tag.asText()));
            return labels.isEmpty()?"无标签":String.join("、",labels);
        }
        if("sourceCode".equals(key)||"industryCode".equals(key))return Objects.toString(mapper.dictionaryLabel("sourceCode".equals(key)?"customer.source":"customer.industry",n.asText()),n.asText());
        String v=n.isContainerNode()?n.toString():n.asText();
        if(key.toLowerCase(Locale.ROOT).contains("phone"))return v.length()<7?"****":v.substring(0,3)+"****"+v.substring(v.length()-4);
        if("email".equals(key)){int at=v.indexOf('@');return at>0?v.substring(0,1)+"***"+v.substring(at):"****";}
        if(v.matches("\\d{4}-\\d{2}-\\d{2}T.*"))return v.replace('T',' ').substring(0,Math.min(v.length(),19));
        return switch(v){case "NEW"->"线索/待首联";case "PENDING"->"待审核";case "CONFIRMED"->"核验通过";case "RETURNED"->"已退回";case "ACTIVE"->"有效/启用";case "STAGNANT"->"停滞";case "SPECIALIST"->"专员";case "MANAGER"->"经理";case "ADMIN"->"管理员";case "KEY"->"重点";case "NORMAL"->"普通";case "WATCH"->"观察";case "LEAD"->"线索";case "FOLLOWING"->"跟进";case "QUOTED", "QUOTING"->"报价";case "DEAL"->"成交";case "WON"->"赢单";case "LOST"->"输单";case "CLOSED"->"关闭";case "DRAFT_GENERATED"->"合同登记草稿";case "DRAFT_REVIEW"->"登记待核对";case "DRAFT_CHECKING"->"登记待核对";case "SIGNED_UPLOAD"->"待上传实际合同";case "WAIT_SIGNED_UPLOAD"->"待上传实际合同";case "FILE_REVIEW"->"合同文件待确认";case "FILE_CONFIRMED"->"合同文件已确认";case "EXCEPTION"->"进度异常";case "WITHDRAWN"->"已撤回";case "true"->"是";case "false"->"否";default->v;};
    }
}
