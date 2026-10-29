/* 文件作用：系统字典及业务参数的数据库实体。 */
package com.bishe.crm.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bishe.crm.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 系统字典及业务参数的数据库实体。 */
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
