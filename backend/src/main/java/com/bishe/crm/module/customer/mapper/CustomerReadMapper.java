package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.customer.vo.CustomerSummaryVO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomerReadMapper {
    String HANDOVER = "EXISTS(SELECT 1 FROM crm_operation_log l WHERE l.biz_type='CUSTOMER' "
            + "AND l.biz_id=c.id AND l.old_owner_id=#{s.userId} AND l.ownership_seq=c.ownership_seq "
            + "AND l.handover_readonly_until &gt; #{now})";
    String OWNED = "<choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when>"
            + "<otherwise>c.owner_id=#{s.userId}</otherwise></choose>";
    String SCOPE = "(c.sea_type='PUBLIC' OR " + OWNED + " OR " + HANDOVER + ")";
    String FIELDS = "SELECT c.id,c.customer_name,CASE WHEN c.sea_type='PUBLIC' THEN "
            + "CASE WHEN c.primary_phone IS NULL THEN NULL WHEN CHAR_LENGTH(c.primary_phone)&gt;=7 "
            + "THEN CONCAT(LEFT(c.primary_phone,3),'****',RIGHT(c.primary_phone,4)) ELSE '****' END "
            + "ELSE c.primary_phone END AS primary_phone,c.source_code,c.industry_code,c.customer_status,c.sea_type,"
            + "c.owner_id,u.real_name AS owner_name,t.team_name,"
            + "CASE WHEN c.sea_type='PUBLIC' THEN 1 WHEN " + OWNED + " THEN 0 ELSE 1 END AS readonly "
            + "FROM crm_customer c LEFT JOIN sys_user u ON u.id=c.owner_id LEFT JOIN sys_team t ON t.id=c.team_id ";

    @Select("<script>" + FIELDS + "WHERE " + SCOPE + " AND c.sea_type=#{sea} ORDER BY c.id DESC</script>")
    IPage<CustomerSummaryVO> list(Page<CustomerSummaryVO> page, @Param("s") DataScope scope,
                                @Param("now") LocalDateTime now, @Param("sea") String sea);

    @Select("<script>" + FIELDS + "WHERE c.id=#{id} AND " + SCOPE + "</script>")
    CustomerSummaryVO detail(@Param("id") Long id, @Param("s") DataScope scope, @Param("now") LocalDateTime now);
}
