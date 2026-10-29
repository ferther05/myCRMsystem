/* 文件作用：实体公共字段，保存主键、版本和创建修改信息。 */
package com.bishe.crm.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 实体公共字段，保存主键、版本和创建修改信息。 */
@Getter
@Setter
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Version
    @TableField(fill = FieldFill.INSERT)
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
