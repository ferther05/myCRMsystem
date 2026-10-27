package com.bishe.crm.module.customer.vo;

public record CustomerDictionaryVO(Long id, String group, String code, String label,
                                   int sortNo, boolean enabled, long version) {}
