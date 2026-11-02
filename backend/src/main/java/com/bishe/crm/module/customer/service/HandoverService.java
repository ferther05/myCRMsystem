/* 文件作用：管理员预览并原子执行同团队批量交接，版本变化要求重新预览。 */
package com.bishe.crm.module.customer.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.dto.HandoverRequests.*;
import com.bishe.crm.module.customer.entity.Customer;
import com.bishe.crm.module.customer.mapper.*;
import com.bishe.crm.module.system.mapper.AccountMapper;
import com.bishe.crm.module.task.mapper.TaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverService {
    private final HandoverMapper mapper;
    private final CustomerMapper customers;
    private final AccountMapper accounts;
    private final TaskMapper audit;
    private final ObjectMapper json;
    private final Clock clock;
    /** 停用账号允许作为移出方，仍不允许登录或作为接收人。 */
    public List<Map<String,Object>> owners(AuthPrincipal a) { admin(a);return mapper.owners(); }
    public List<Map<String,Object>> candidates(AuthPrincipal a,Long owner) {
        admin(a);var rows=mapper.candidates(owner);
        if(rows.size()>500) throw new BizException(ErrorCode.BAD_REQUEST);
        return rows;
    }
    /** 预览读取关联版本，不写业务。确认时重新锁定、比对后才执行。 */
    @Transactional
    public Map<String,Object> preview(AuthPrincipal a,Selection r) { admin(a);return prepare(r,false,a,null); }
    /** 一批最多50客户，任一不匹配或审计失败则整批回滚。 */
    @Transactional
    public Map<String,Object> execute(AuthPrincipal a,Execute r) {
        admin(a);var preview=prepare(r.selection(),false,a,null);
        if(!Objects.equals(preview.get("previewToken"),r.previewToken())) throw new BizException(ErrorCode.CONFLICT);
        return prepare(r.selection(),true,a,r.reason().trim());
    }
    /** 固定客户及账号锁顺序，关联行逐条带版本更新，历史业绩不改。 */
    private Map<String,Object> prepare(Selection r,boolean write,AuthPrincipal a,String reason) {
        if(r.fromOwnerId().equals(r.toOwnerId())||new HashSet<>(r.customerIds()).size()!=r.customerIds().size()) throw new BizException(ErrorCode.VALIDATION_ERROR);
        List<Customer> cs=new ArrayList<>();
        for(Long id:r.customerIds().stream().sorted().toList()) {
            var c=customers.lock(id);
            if(c==null||!"PRIVATE".equals(c.getSeaType())||!r.fromOwnerId().equals(c.getOwnerId())) throw new BizException(ErrorCode.CONFLICT);
            cs.add(c);
        }
        var first=accounts.lockUser(Math.min(r.fromOwnerId(),r.toOwnerId()));
        var second=accounts.lockUser(Math.max(r.fromOwnerId(),r.toOwnerId()));
        var source=Objects.equals(first==null?null:first.getId(),r.fromOwnerId())?first:second;
        var target=Objects.equals(first==null?null:first.getId(),r.toOwnerId())?first:second;
        if(source==null||!AuthUsers.usable(target)||!"SPECIALIST".equals(target.getRoleCode())||!Objects.equals(source.getTeamId(),target.getTeamId())||accounts.lockEnabledTeam(target.getTeamId())==null) throw new BizException(ErrorCode.ACCOUNT_RULE);
        List<Object> state=new ArrayList<>();int ops=0,contracts=0,todos=0;
        LocalDateTime now=LocalDateTime.now(clock);
        for(var c:cs) {
            if(!Objects.equals(c.getTeamId(),target.getTeamId())) throw new BizException(ErrorCode.ACCOUNT_RULE);
            var os=mapper.opportunities(c.getId());var ks=mapper.contracts(c.getId());var ts=mapper.todos(c.getId(),source.getId());
            state.add(List.of(c.getId(),c.getVersion(),os,ks,ts));ops+=os.size();contracts+=ks.size();todos+=ts.size();
            if(write) {
                changed(customers.transfer(c.getId(),c.getVersion(),target.getId(),target.getTeamId(),a.id(),now));
                for(var o:os) {
                    changed(mapper.moveOpportunity(number(o,"id"),number(o,"version"),target.getId(),target.getTeamId()));
                    mapper.endOwnerCollaboration(number(o,"id"),source.getId(),target.getId(),now);
                }
                for(var k:ks) changed(mapper.moveContract(number(k,"id"),number(k,"version"),target.getId(),target.getTeamId()));
                for(var t:ts) { mapper.copyTodo(number(t,"id"),target.getId());changed(mapper.cancelTodo(number(t,"id"),number(t,"version"),now)); }
                String before=encode(Map.of("ownerId",source.getId(),"teamId",c.getTeamId()));
                String after=encode(Map.of("ownerId",target.getId(),"teamId",target.getTeamId()));
                changed(customers.ownershipAudit(c.getId(),"OWNER_TRANSFERRED",a.id(),a.teamId(),now,c.getOwnershipSeq()+1,source.getId(),target.getId(),target.getTeamId(),now.plusDays(7),before,after,reason));
                for(var o:os) auditEntity("OPPORTUNITY",number(o,"id"),a,now,source.getId(),target.getId(),reason);
                for(var k:ks) auditEntity("CONTRACT",number(k,"id"),a,now,source.getId(),target.getId(),reason);
            }
        }
        if(write) { accounts.syncHoldCount(source.getId(),customers.privateHoldCount(source.getId()));accounts.syncHoldCount(target.getId(),customers.privateHoldCount(target.getId())); }
        return Map.of("customers",cs.size(),"opportunities",ops,"contracts",contracts,"pendingTodos",todos,"previewToken",digest(encode(List.of(r.fromOwnerId(),r.toOwnerId(),state))));
    }
    /** 使用现有同事务审计，关联业务保留单独可查询事件。 */
    private void auditEntity(String biz,Long id,AuthPrincipal a,LocalDateTime now,Long from,Long to,String reason) {
        changed(audit.handoverAudit(biz,id,a.id(),a.teamId(),now,"负责人交接："+audit.userName(from)+" → "+audit.userName(to)+"；"+reason,
            encode(Map.of("ownerId",from)),encode(Map.of("ownerId",to))));
    }
    private Long number(Map<String,Object> m,String k) { return ((Number)m.get(k)).longValue(); }
    private String encode(Object v) { try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);} }
    private String digest(String v) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);} }
    private void changed(int n) {if(n!=1)throw new BizException(ErrorCode.CONFLICT);}
    private void admin(AuthPrincipal a) {if(a==null||a.mustChangePassword())throw new BizException(ErrorCode.PASSWORD_CHANGE_REQUIRED);if(!"ADMIN".equals(a.roleCode()))throw new BizException(ErrorCode.FORBIDDEN);}
}
