/* 文件作用：新增和更新实体时填充时间字段。 */
package com.bishe.crm.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** 新增和更新实体时填充时间字段。 */
@Component
@RequiredArgsConstructor
public class AuditFieldHandler implements MetaObjectHandler {

    private final Clock businessClock;

    /** 新增实体时填充创建和修改时间。 */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(businessClock);
        strictInsertFill(metaObject, "version", Long.class, 0L);
        setFieldValByName("createdAt", now, metaObject);
        setFieldValByName("updatedAt", now, metaObject);
    }

    /** 更新实体时填充修改时间。 */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", LocalDateTime.now(businessClock), metaObject);
    }
}
