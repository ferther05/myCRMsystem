/* 文件作用：跟进记录新增、查验和有效时间重算的数据访问。 */
package com.bishe.crm.module.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bishe.crm.module.customer.entity.FollowRecord;
import com.bishe.crm.module.customer.vo.FollowRecordVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 跟进记录新增、查验和有效时间重算的数据访问。 */
@Mapper
public interface FollowRecordMapper extends BaseMapper<FollowRecord> {
    /** 查询客户跟进时间线。 */
    @Select(
        "SELECT f.id,f.customer_id,f.opportunity_id,f.opportunity_stage_code,f.opportunity_stage_seq,f.contact_id,c.contact_name,f.follow_user_id,u.real_name follow_user_name," +
            "f.follow_method,f.content,f.result_code,f.substantive_result,f.next_action,f.next_action_at," +
            "f.is_effective effective,f.review_status,f.reviewer_id,r.real_name reviewer_name,f.reviewed_at," +
            "f.review_reason,f.followed_at,f.version FROM crm_follow_record f " +
            "JOIN sys_user u ON u.id=f.follow_user_id LEFT JOIN sys_user r ON r.id=f.reviewer_id " +
            "LEFT JOIN crm_contact c ON c.id=f.contact_id WHERE f.customer_id=#{customerId} ORDER BY f.followed_at DESC,f.id DESC"
    )
    List<FollowRecordVO> byCustomer(@Param("customerId") Long customerId);

    /** 查询当前商机关联跟进。 */
    @Select(
        "SELECT f.id,f.customer_id,f.opportunity_id,f.opportunity_stage_code,f.opportunity_stage_seq,f.contact_id,c.contact_name,f.follow_user_id,u.real_name follow_user_name,f.follow_method,f.content,f.result_code,f.substantive_result,f.next_action,f.next_action_at,f.is_effective effective,f.review_status,f.reviewer_id,r.real_name reviewer_name,f.reviewed_at,f.review_reason,f.followed_at,f.version FROM crm_follow_record f JOIN sys_user u ON u.id=f.follow_user_id LEFT JOIN sys_user r ON r.id=f.reviewer_id LEFT JOIN crm_contact c ON c.id=f.contact_id WHERE f.opportunity_id=#{id} ORDER BY f.followed_at DESC,f.id DESC"
    )
    List<FollowRecordVO> byOpportunity(Long id);

    /** 新增原始跟进记录及有效性信息。 */
    @Insert(
        "INSERT INTO crm_follow_record(customer_id,opportunity_id,opportunity_stage_code,opportunity_stage_seq,contact_id,follow_user_id,record_type,follow_method,content," +
            "substantive_result,result_code,next_action,next_action_at,is_effective,review_status,reviewer_id,reviewed_at,review_reason,followed_at,created_at,version) " +
            "VALUES(#{f.customerId},#{f.opportunityId},#{f.opportunityStageCode},#{f.opportunityStageSeq},#{f.contactId},#{f.followUserId},'FOLLOW',#{f.followMethod},#{f.content}," +
            "#{f.substantiveResult},#{f.resultCode},#{f.nextAction},#{f.nextActionAt},#{f.isEffective},#{f.reviewStatus},#{f.reviewerId},#{f.reviewedAt},#{f.reviewReason},#{f.followedAt},#{f.createdAt},0)"
    )
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "f.id")
    int insertFollow(@Param("f") FollowRecord record);

    /** 锁定未删除记录，供事务内条件更新。 */
    @Select(
        "SELECT * FROM crm_follow_record WHERE id=#{id} AND customer_id=#{customerId} FOR UPDATE"
    )
    FollowRecord lock(@Param("id") Long id, @Param("customerId") Long customerId);

    /** 按当前审核状态更新处理结论。 */
    @Update(
        "UPDATE crm_follow_record SET review_status=#{status},is_effective=#{effective},reviewer_id=#{reviewer}," +
            "reviewed_at=#{now},review_reason=#{reason},version=version+1 WHERE id=#{id} AND version=#{version} AND review_status='PENDING'"
    )
    int review(
        @Param("id") Long id,
        @Param("version") long version,
        @Param("status") String status,
        @Param("effective") boolean effective,
        @Param("reviewer") Long reviewer,
        @Param("now") LocalDateTime now,
        @Param("reason") String reason
    );

    /** 查询客户剩余有效跟进的最新时间。 */
    @Select(
        "SELECT MAX(f.followed_at) FROM crm_follow_record f JOIN crm_customer c ON c.id=f.customer_id WHERE f.customer_id=#{customerId} AND f.is_effective=1 AND (c.ownership_started_at IS NULL OR f.created_at>=c.ownership_started_at)"
    )
    LocalDateTime latestEffectiveAt(Long customerId);

    /** 统计待查验的有效跟进。 */
    @Select(
        "SELECT COUNT(*) FROM crm_follow_record f JOIN crm_customer c ON c.id=f.customer_id WHERE f.customer_id=#{customerId} AND f.review_status='PENDING' AND f.is_effective=1 AND (c.ownership_started_at IS NULL OR f.created_at>=c.ownership_started_at)"
    )
    long pendingEffective(Long customerId);

    /** 查询当前商机有效跟进的最新时间。 */
    @Select(
        "SELECT MAX(followed_at) FROM crm_follow_record WHERE opportunity_id=#{opportunityId} AND is_effective=1"
    )
    LocalDateTime latestEffectiveAtForOpportunity(@Param("opportunityId") Long opportunityId);

    /** 仅当前阶段本轮已核验有效跟进满足推进门槛；待查验只影响活跃计时。 */
    @Select(
        "SELECT COUNT(*) FROM crm_follow_record WHERE opportunity_id=#{id} AND opportunity_stage_code=#{stage} AND opportunity_stage_seq=#{seq} AND is_effective=1 AND review_status='CONFIRMED'"
    )
    long confirmedInStage(
        @Param("id") Long id,
        @Param("stage") String stage,
        @Param("seq") Long seq
    );
}
