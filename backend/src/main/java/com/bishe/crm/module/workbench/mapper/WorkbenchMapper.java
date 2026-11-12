/* 文件作用：按服务端身份查询工作项，计数与分页使用同一条件。 */
package com.bishe.crm.module.workbench.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkbenchMapper {
    /** 全集计数，不以预览行数代替。 */
    long count(@Param("s") DataScope scope, @Param("category") String category, @Param("today") LocalDate today);
    /** 固定排序与稳定主键保证相邻页可重复定位。 */
    IPage<Map<String,Object>> items(Page<Map<String,Object>> page, @Param("s") DataScope scope,
        @Param("category") String category, @Param("today") LocalDate today);
}
