/* 文件作用：客户联系人与主联系人的数据访问。 */
package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bishe.crm.module.customer.entity.Contact;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 客户联系人与主联系人的数据访问。 */
@Mapper
public interface ContactMapper extends BaseMapper<Contact> {
    /** 查询客户未删除的联系人。 */
    @Select("SELECT * FROM crm_contact WHERE customer_id=#{id} ORDER BY is_primary DESC,id")
    List<Contact> forCustomer(Long id);

    /** 查询客户当前主联系人。 */
    @Select("SELECT * FROM crm_contact WHERE customer_id=#{id} AND is_primary=1")
    Contact primary(Long id);

    /** 取消客户现有主联系人标记。 */
    @Update(
        "UPDATE crm_contact SET is_primary=0,version=version+1,updated_at=#{now} WHERE customer_id=#{customer} AND is_primary=1"
    )
    int clearPrimary(@Param("customer") Long id, @Param("now") LocalDateTime now);

    /** 统计联系人被跟进引用的数量。 */
    @Select(
        "SELECT (SELECT COUNT(*) FROM crm_follow_record WHERE contact_id=#{id})+(SELECT COUNT(*) FROM crm_reminder WHERE contact_id=#{id})"
    )
    long references(Long id);

    /** 按版本保存修改字段。 */
    @Update(
        "UPDATE crm_contact SET contact_name=#{c.contactName},job_title=#{c.jobTitle},phone=#{c.phone},email=#{c.email},birthday=#{c.birthday}," +
            "decision_role=#{c.decisionRole},is_primary=#{c.isPrimary},version=version+1,updated_at=#{now} " +
            "WHERE id=#{c.id} AND customer_id=#{c.customerId} AND version=#{expectedVersion}"
    )
    int save(
        @Param("c") Contact contact,
        @Param("now") LocalDateTime now,
        @Param("expectedVersion") long expectedVersion
    );
}
