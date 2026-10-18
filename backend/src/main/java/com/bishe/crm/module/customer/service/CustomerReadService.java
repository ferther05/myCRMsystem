package com.bishe.crm.module.customer.service;

import com.bishe.crm.common.api.*;
import com.bishe.crm.common.exception.BizException;
import com.bishe.crm.module.auth.security.*;
import com.bishe.crm.module.customer.mapper.CustomerReadMapper;
import com.bishe.crm.module.customer.vo.CustomerSummaryVO;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CustomerReadService {
    private final CustomerReadMapper customers;
    private final Clock businessClock;

    public PageResult<CustomerSummaryVO> list(AuthPrincipal actor, PageQuery query, String sea) {
        return PageResult.from(customers.list(query.toPage(), DataScope.of(actor), LocalDateTime.now(businessClock), sea));
    }

    public CustomerSummaryVO detail(AuthPrincipal actor, Long id) {
        var customer = customers.detail(id, DataScope.of(actor), LocalDateTime.now(businessClock));
        if (customer == null) throw new BizException(ErrorCode.NOT_FOUND);
        return customer;
    }
}
