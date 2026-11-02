/* 文件作用：按当前范围和历史快照分别聚合看板，避免多表JOIN造成金额重复累计。 */
package com.bishe.crm.module.analytics.mapper;

import com.bishe.crm.module.analytics.vo.DashboardViews;
import com.bishe.crm.module.auth.security.DataScope;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DashboardMapper {
    /** 只返回本人、本团队或管理员范围内的筛选人员，包含历史停用人员。 */
    List<DashboardViews.Owner> owners(@Param("s") DataScope scope);
    /** 当前存量与时间范围内的业绩分别计算。 */
    Map<String,Object> metrics(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end,@Param("today") LocalDate today);
    /** 当前阶段分布，按商机创建时间筛选。 */
    List<Map<String,Object>> stages(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 签约与到账分别先聚合再组合，不互相乘倍。 */
    List<Map<String,Object>> trends(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 排行按历史业绩人员聚合，顺序由服务端固定。 */
    List<Map<String,Object>> ranking(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 同一创建批次的历史到达成交、赢单记录，不用当前阶段数量冒充转化。 */
    Map<String,Object> conversion(@Param("s") DataScope scope,@Param("owner") Long owner,@Param("start") LocalDateTime start,@Param("end") LocalDateTime end);
    /** 本人当前待办始终实时查询，不把管理员待办分享给其他角色。 */
    List<Map<String,Object>> actions(Long actor);
}
