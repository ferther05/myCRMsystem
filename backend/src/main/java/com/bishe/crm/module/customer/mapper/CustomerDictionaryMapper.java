/* 文件作用：客户字典、负责人选项及字典审计的数据访问。 */
package com.bishe.crm.module.customer.mapper;

import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.customer.vo.CustomerDictionaryVO;
import com.bishe.crm.module.system.entity.SystemConfig;
import com.bishe.crm.module.system.vo.UserVO;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 客户字典、负责人选项及字典审计的数据访问。 */
@Mapper
public interface CustomerDictionaryMapper {
    /** 查询客户分类字典及启停信息。 */
    @Select(
        "SELECT id,SUBSTRING(config_group,10) AS `group`,config_key AS code,display_name AS label,sort_no,enabled,version " +
            "FROM sys_config WHERE config_type='DICT' AND config_group IN ('customer.source','customer.industry','customer.level','customer.tag') ORDER BY config_group,sort_no,id"
    )
    List<CustomerDictionaryVO> all();

    /** 锁定未删除记录，供事务内条件更新。 */
    @Select("SELECT * FROM sys_config WHERE id=#{id} FOR UPDATE")
    SystemConfig lock(Long id);

    /** 查询可分配的启用经理和专员。 */
    @Select(
        "<script>SELECT u.id,u.username,u.real_name,u.role_code,u.team_id,t.team_name FROM sys_user u JOIN sys_team t ON t.id=u.team_id " +
            "WHERE u.account_status='ACTIVE' AND t.enabled=1 AND u.role_code IN ('MANAGER','SPECIALIST') " +
            "<choose><when test=\"s.role == 'ADMIN'\"></when><when test=\"s.role == 'MANAGER'\">AND u.team_id=#{s.teamId}</when>" +
            "<otherwise>AND u.id=#{s.userId}</otherwise></choose> ORDER BY u.team_id,u.id</script>"
    )
    List<UserVO> owners(@Param("s") DataScope scope);

    /** 写入本次业务变更的审计快照。 */
    @Insert(
        "INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,occurred_at,reason) " +
            "VALUES('CONFIG',#{id},#{action},#{actor},CURRENT_TIMESTAMP(3),'维护客户分类选项，编码固定，停用保留历史')"
    )
    int audit(@Param("id") Long id, @Param("actor") Long actor, @Param("action") String action);
}
