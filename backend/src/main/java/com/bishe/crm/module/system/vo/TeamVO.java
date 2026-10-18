package com.bishe.crm.module.system.vo;

public record TeamVO(Long id, String teamCode, String teamName, Long managerId,
                     String managerName, Boolean enabled) { }
