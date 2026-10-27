package com.bishe.crm.module.customer.service;

import com.bishe.crm.common.api.ErrorCode;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.dto.CustomerRequests;
import com.bishe.crm.module.customer.mapper.CustomerDictionaryMapper;
import com.bishe.crm.module.customer.vo.CustomerDictionaryVO;
import com.bishe.crm.module.system.entity.SystemConfig;
import com.bishe.crm.module.system.mapper.SystemConfigMapper;
import com.bishe.crm.module.system.vo.UserVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CustomerDictionaryService {
    private final CustomerDictionaryMapper dictionaries;
    private final SystemConfigMapper configs;
    private final CustomerService customers;

    public List<CustomerDictionaryVO> list() { return dictionaries.all(); }
    public List<UserVO> owners(AuthPrincipal actor) { return dictionaries.owners(DataScope.of(actor)); }

    @Transactional
    public void create(AuthPrincipal actor,CustomerRequests.DictionaryWrite request) {
        admin(actor); customers.begin(actor);
        SystemConfig config = new SystemConfig();
        config.setConfigType("DICT"); config.setConfigGroup("customer."+request.group());
        config.setConfigKey(request.code()); config.setConfigValue(request.code());
        config.setDisplayName(label(request.label())); config.setSortNo(request.sortNo()); config.setEnabled(true);
        changed(configs.insert(config));
        changed(dictionaries.audit(config.getId(),actor.id(),"CUSTOMER_DICTIONARY_CREATED"));
    }

    @Transactional
    public void edit(AuthPrincipal actor,Long id,CustomerRequests.DictionaryEdit request) {
        admin(actor); customers.begin(actor);
        SystemConfig config = dictionaries.lock(id);
        if (config == null || !"DICT".equals(config.getConfigType())
                || !List.of("customer.source","customer.industry","customer.tag").contains(config.getConfigGroup())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!config.getVersion().equals(request.version())) throw new BizException(ErrorCode.CONFLICT);
        config.setDisplayName(label(request.label())); config.setSortNo(request.sortNo()); config.setEnabled(request.enabled());
        changed(configs.updateById(config));
        changed(dictionaries.audit(id,actor.id(),"CUSTOMER_DICTIONARY_UPDATED"));
    }
    private void admin(AuthPrincipal actor) { if (!"ADMIN".equals(actor.roleCode())) throw new BizException(ErrorCode.FORBIDDEN); }
    private void changed(int count) { if (count != 1) throw new BizException(ErrorCode.CONFLICT); }
    private String label(String value) { if (value.isBlank()) throw new BizException(ErrorCode.VALIDATION_ERROR); return value.trim(); }
}
