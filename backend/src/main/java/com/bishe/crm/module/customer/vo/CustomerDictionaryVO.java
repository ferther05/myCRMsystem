/* 文件作用：返回客户分类字典选项。 */
package com.bishe.crm.module.customer.vo;

/** 返回客户分类字典选项。 */
public record CustomerDictionaryVO(
    Long id,
    String group,
    String code,
    String label,
    int sortNo,
    boolean enabled,
    long version
) {}
