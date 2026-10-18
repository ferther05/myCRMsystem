package com.bishe.crm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bishe.crm.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_config")
public class SystemConfig extends BaseEntity {
    private String configType;
    private String configGroup;
    private String configKey;
    private String configValue;
    private String displayName;
    private Integer sortNo;
    private Boolean enabled;
}
