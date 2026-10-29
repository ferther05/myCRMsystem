/* 文件作用：通过最小查询检查数据库连接。 */
package com.bishe.crm.module.health.mapper;

import org.apache.ibatis.annotations.Select;

/** 通过最小查询检查数据库连接。 */
public interface DatabaseProbeMapper {
    /** 执行最小数据库连通查询。 */
    @Select("SELECT 1")
    Integer ping();
}
