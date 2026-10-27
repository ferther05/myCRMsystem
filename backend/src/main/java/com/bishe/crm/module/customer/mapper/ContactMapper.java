package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bishe.crm.module.customer.entity.Contact;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ContactMapper extends BaseMapper<Contact> {
    @Select("SELECT * FROM crm_contact WHERE customer_id=#{id} ORDER BY is_primary DESC,id")
    List<Contact> forCustomer(Long id);
    @Select("SELECT * FROM crm_contact WHERE customer_id=#{id} AND is_primary=1")
    Contact primary(Long id);
    @Update("UPDATE crm_contact SET is_primary=0,version=version+1,updated_at=#{now} WHERE customer_id=#{customer} AND is_primary=1")
    int clearPrimary(@Param("customer") Long id, @Param("now") LocalDateTime now);
    @Select("SELECT (SELECT COUNT(*) FROM crm_follow_record WHERE contact_id=#{id})+(SELECT COUNT(*) FROM crm_reminder WHERE contact_id=#{id})")
    long references(Long id);
    @Update("UPDATE crm_contact SET contact_name=#{c.contactName},job_title=#{c.jobTitle},phone=#{c.phone},email=#{c.email},birthday=#{c.birthday},"
            + "decision_role=#{c.decisionRole},is_primary=#{c.isPrimary},version=version+1,updated_at=#{now} "
            + "WHERE id=#{c.id} AND customer_id=#{c.customerId} AND version=#{expectedVersion}")
    int save(@Param("c") Contact contact,@Param("now") LocalDateTime now,
             @Param("expectedVersion") long expectedVersion);
}
