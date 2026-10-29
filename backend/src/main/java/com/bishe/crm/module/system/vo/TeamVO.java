/* 文件作用：返回团队及负责人选项。 */
package com.bishe.crm.module.system.vo;

/** 返回团队及负责人选项。 */
public record TeamVO(
    Long id,
    String teamCode,
    String teamName,
    Long managerId,
    String managerName,
    Boolean enabled
) {}
