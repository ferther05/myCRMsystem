/* 文件作用：合同登记、附件、签署历史和相关联动的数据访问。 */
package com.bishe.crm.module.contract.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.contract.dto.ContractQuery;
import com.bishe.crm.module.contract.entity.Contract;
import com.bishe.crm.module.contract.entity.ContractAttachment;
import com.bishe.crm.module.contract.vo.ContractAttachmentVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 合同登记、附件、签署历史和相关联动的数据访问。 */
@Mapper
public interface ContractMapper {

    /** 首次文件核验通过时冻结业绩归属和金额，后续暂停恢复不能重复计业绩。 */
    @Update("UPDATE crm_contract SET performance_owner_id=owner_id,performance_team_id=team_id,performance_confirmed_at=#{now},performance_amount=contract_amount,performance_legacy=0 WHERE id=#{id} AND performance_confirmed_at IS NULL")
    int capturePerformance(@Param("id") Long id,@Param("now") java.time.LocalDateTime now);
    String SCOPE =
        "<choose><when test=\"s.role == 'ADMIN'\">1=1</when><otherwise>EXISTS(SELECT 1 FROM crm_customer scope_customer WHERE scope_customer.id=c.customer_id AND scope_customer.deleted=0 AND scope_customer.sea_type='PRIVATE' AND <choose><when test=\"s.role == 'MANAGER'\">scope_customer.team_id=#{s.teamId}</when><otherwise>scope_customer.owner_id=#{s.userId}</otherwise></choose>)</otherwise></choose>";
    String FIELDS =
        "SELECT c.id,c.contract_no,c.external_contract_no,c.customer_id,cu.customer_name,c.opportunity_id,o.opportunity_name," +
        "c.owner_id,u.real_name owner_name,c.team_id,t.team_name,c.contract_name,c.business_type,c.our_party_name,c.counterparty_name," +
        "c.contract_amount,c.signed_at,c.expire_date,c.contract_status,c.signing_progress,c.resume_signing_progress,c.payment_status,c.received_amount,c.outstanding_amount," +
        "c.version,c.created_at,c.updated_at FROM crm_contract c JOIN crm_customer cu ON cu.id=c.customer_id " +
        "JOIN crm_opportunity o ON o.id=c.opportunity_id JOIN sys_user u ON u.id=c.owner_id JOIN sys_team t ON t.id=c.team_id ";

    /** 按权限和筛选条件分页查询。 */
    @Select(
        "<script>" +
            FIELDS +
            "WHERE " +
            SCOPE +
            "<if test=\"q.keyword != null and q.keyword != ''\"> AND (c.contract_name LIKE CONCAT('%',#{q.keyword},'%') OR cu.customer_name LIKE CONCAT('%',#{q.keyword},'%') OR c.contract_no LIKE CONCAT('%',#{q.keyword},'%'))</if>" +
            "<if test=\"q.contractStatus != null and q.contractStatus != ''\"> AND c.contract_status=#{q.contractStatus}</if>" +
            "<if test=\"q.signingProgress != null and q.signingProgress != ''\"> AND c.signing_progress=#{q.signingProgress}</if>" +
            "<if test=\"q.opportunityId != null\"> AND c.opportunity_id=#{q.opportunityId}</if>" +
            " ORDER BY c.id DESC</script>"
    )
    IPage<ContractSummaryVO> list(
        Page<ContractSummaryVO> page,
        @Param("s") DataScope scope,
        @Param("q") ContractQuery q
    );

    /** 按范围分页查询可登记合同的成交商机。 */
    @Select(
        "<script>SELECT o.id opportunity_id,o.opportunity_name,o.customer_id,cu.customer_name," +
            "o.owner_id,u.real_name owner_name,o.team_id,t.team_name,o.expected_amount,o.expected_close_date," +
            "c.id contract_id,c.contract_no,c.contract_status,c.signing_progress " +
            "FROM crm_opportunity o JOIN crm_customer cu ON cu.id=o.customer_id AND cu.deleted=0 " +
            "JOIN sys_user u ON u.id=o.owner_id JOIN sys_team t ON t.id=o.team_id " +
            "LEFT JOIN crm_contract c ON c.id=(SELECT MAX(cc.id) FROM crm_contract cc WHERE cc.opportunity_id=o.id AND cc.contract_status != 'VOID') " +
            "WHERE o.stage_code='DEAL' AND <choose><when test=\"s.role == 'ADMIN'\">1=1</when>" +
            "<when test=\"s.role == 'MANAGER'\">o.team_id=#{s.teamId}</when><otherwise>o.owner_id=#{s.userId}</otherwise></choose>" +
            "<if test=\"q.keyword != null and q.keyword != ''\"> AND (o.opportunity_name LIKE CONCAT('%',#{q.keyword},'%') OR cu.customer_name LIKE CONCAT('%',#{q.keyword},'%') OR c.contract_no LIKE CONCAT('%',#{q.keyword},'%'))</if>" +
            "<if test=\"q.contractStatus == 'UNREGISTERED'\"> AND c.id IS NULL</if>" +
            "<if test=\"q.contractStatus != null and q.contractStatus != '' and q.contractStatus != 'UNREGISTERED'\"> AND c.contract_status=#{q.contractStatus}</if>" +
            "<if test=\"q.signingProgress != null and q.signingProgress != ''\"> AND c.signing_progress=#{q.signingProgress}</if>" +
            "<if test=\"q.opportunityId != null\"> AND o.id=#{q.opportunityId}</if>" +
            " ORDER BY o.id DESC</script>"
    )
    IPage<ContractRegistrationVO> registrationOpportunities(
        Page<ContractRegistrationVO> page,
        @Param("s") DataScope scope,
        @Param("q") ContractQuery q
    );

    /** 查询指定记录的可见摘要。 */
    @Select("<script>" + FIELDS + "WHERE c.id=#{id} AND " + SCOPE + "</script>")
    ContractSummaryVO summary(@Param("id") Long id, @Param("s") DataScope scope);

    /** 锁定未删除记录，供事务内条件更新。 */
    @Select("SELECT * FROM crm_contract WHERE id=#{id} FOR UPDATE")
    Contract lock(Long id);

    /** 按主键查询当前记录。 */
    @Select("SELECT * FROM crm_contract WHERE id=#{id}")
    Contract find(Long id);

    /** 查询商机现有的有效合同登记。 */
    @Select(
        "SELECT * FROM crm_contract WHERE opportunity_id=#{opportunityId} AND contract_status <> 'VOID' ORDER BY id DESC LIMIT 1 FOR UPDATE"
    )
    Contract currentByOpportunity(Long opportunityId);

    /** 新增数据库记录。 */
    @Insert(
        "INSERT INTO crm_contract(contract_no,external_contract_no,customer_id,opportunity_id,owner_id,team_id,contract_name,business_type,our_party_name,counterparty_name,description,contract_amount,contract_status,signing_progress,payment_status,received_amount,outstanding_amount,version,created_by,created_at,updated_at) " +
            "VALUES(#{c.contractNo},#{c.externalContractNo},#{c.customerId},#{c.opportunityId},#{c.ownerId},#{c.teamId},#{c.contractName},#{c.businessType},#{c.ourPartyName},#{c.counterpartyName},#{c.description},#{c.contractAmount},'DRAFT','DRAFT_GENERATED','UNPAID',0,#{c.outstandingAmount},0,#{c.createdBy},#{c.createdAt},#{c.updatedAt})"
    )
    @Options(useGeneratedKeys = true, keyProperty = "c.id")
    int insert(@Param("c") Contract contract);

    /** 按版本更新合同登记字段。 */
    @Update(
        "UPDATE crm_contract SET contract_name=#{c.contractName},external_contract_no=#{c.externalContractNo},business_type=#{c.businessType},our_party_name=#{c.ourPartyName},counterparty_name=#{c.counterpartyName},contract_amount=#{c.contractAmount},signed_at=#{c.signedAt},expire_date=#{c.expireDate},description=#{c.description},our_contact_name=#{c.ourContactName},our_contact_phone=#{c.ourContactPhone},counterparty_contact_name=#{c.counterpartyContactName},counterparty_contact_phone=#{c.counterpartyContactPhone},our_address=#{c.ourAddress},counterparty_address=#{c.counterpartyAddress},our_credit_code=#{c.ourCreditCode},counterparty_credit_code=#{c.counterpartyCreditCode},payment_terms=#{c.paymentTerms},delivery_terms=#{c.deliveryTerms},planned_signed_at=#{c.plannedSignedAt},performance_start_date=#{c.performanceStartDate},outstanding_amount=#{c.contractAmount}-received_amount,version=version+1,updated_at=#{now} WHERE id=#{c.id} AND version=#{c.version} AND contract_status IN ('DRAFT','RETURNED')"
    )
    int updateDraft(@Param("c") Contract contract, @Param("now") LocalDateTime now);

    /** 在已获合同锁的事务内记录或撤销登记核对事实，由随后状态流转递增版本。 */
    @Update(
        "UPDATE crm_contract SET registration_approved_at=#{at},registration_approved_by=#{reviewer} WHERE id=#{c.id} AND version=#{c.version}"
    )
    int registrationApproval(
        @Param("c") Contract c,
        @Param("at") LocalDateTime at,
        @Param("reviewer") Long reviewer
    );

    /** 按版本更新合同内部状态。 */
    @Update(
        "UPDATE crm_contract SET contract_status=#{status},signed_at=COALESCE(#{signedAt},signed_at),version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status=#{expected}"
    )
    int updateStatus(
        @Param("id") Long id,
        @Param("version") Long version,
        @Param("expected") String expected,
        @Param("status") String status,
        @Param("signedAt") java.time.LocalDate signedAt,
        @Param("now") LocalDateTime now
    );

    /** 在合同锁内按原进度、内部状态和版本原子推进或暂停，保留恢复节点。 */
    @Update(
        "UPDATE crm_contract SET signing_progress=#{to},contract_status=#{status},resume_signing_progress=#{resume},version=version+1,updated_at=#{now} WHERE id=#{c.id} AND version=#{c.version} AND signing_progress=#{c.signingProgress} AND contract_status=#{c.contractStatus}"
    )
    int transition(
        @Param("c") Contract c,
        @Param("to") String to,
        @Param("status") String status,
        @Param("resume") String resume,
        @Param("now") LocalDateTime now
    );

    /** 查询指定环节的最新版本，历史版本不替代被退回的新版本。 */
    @Select(
        "SELECT * FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{id} AND material_type=#{type} ORDER BY version_no DESC LIMIT 1"
    )
    ContractAttachment latestMaterial(@Param("id") Long id, @Param("type") String type);

    /** 新增签署进度历史。 */
    @Insert(
        "INSERT INTO crm_contract_signing_history(contract_id,from_progress,to_progress,operator_id,operator_name,operator_role,operator_team_id,changed_at,reason,is_correction) VALUES(#{id},#{from},#{to},#{actorId},#{actorName},#{role},#{teamId},#{now},#{reason},#{correction})"
    )
    int insertSigningHistory(
        @Param("id") Long id,
        @Param("from") String from,
        @Param("to") String to,
        @Param("actorId") Long actorId,
        @Param("actorName") String actorName,
        @Param("role") String role,
        @Param("teamId") Long teamId,
        @Param("now") LocalDateTime now,
        @Param("reason") String reason,
        @Param("correction") boolean correction
    );

    /** 查询签署进度历史。 */
    @Select(
        "SELECT h.id,h.from_progress fromProgress,h.to_progress toProgress,h.operator_name operatorName,h.operator_role operatorRole,h.changed_at changedAt,h.reason,h.is_correction correction FROM crm_contract_signing_history h WHERE h.contract_id=#{id} ORDER BY h.id DESC"
    )
    List<java.util.Map<String, Object>> signingHistory(Long id);

    /** 统计双方签署文件数量。 */
    @Select(
        "SELECT COUNT(*) FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{id} AND material_type='SIGNED_CONTRACT'"
    )
    long signedAttachmentCount(Long id);

    /** 计算下一附件版本号。 */
    @Select(
        "SELECT COALESCE(MAX(version_no),0)+1 FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type=#{materialType}"
    )
    int nextAttachmentVersion(
        @Param("contractId") Long contractId,
        @Param("materialType") String materialType
    );

    /** 新增附件存储与版本记录。 */
    @Insert(
        "INSERT INTO crm_attachment(biz_type,biz_id,material_type,version_no,file_name,storage_path,file_size,uploaded_by,uploaded_at,credential_note,confirm_status,version,created_at,updated_at) VALUES('CONTRACT',#{a.bizId},#{a.materialType},#{a.versionNo},#{a.fileName},#{a.storagePath},#{a.fileSize},#{a.uploadedBy},#{a.uploadedAt},#{a.credentialNote},'PENDING',0,#{a.uploadedAt},#{a.uploadedAt})"
    )
    @Options(useGeneratedKeys = true, keyProperty = "a.id")
    int insertAttachment(@Param("a") ContractAttachment attachment);

    /** 查询合同全部附件版本。 */
    @Select(
        "SELECT a.id,a.material_type materialType,a.version_no versionNo,a.file_name fileName,a.file_size fileSize,a.uploaded_by uploadedBy,up.real_name uploaderName,a.uploaded_at uploadedAt,a.credential_note credentialNote,a.confirm_status confirmStatus,a.confirm_opinion confirmOpinion,a.confirmed_by confirmedBy,cp.real_name confirmerName,a.confirmed_at confirmedAt FROM crm_attachment a JOIN sys_user up ON up.id=a.uploaded_by LEFT JOIN sys_user cp ON cp.id=a.confirmed_by WHERE a.biz_type='CONTRACT' AND a.biz_id=#{contractId} ORDER BY a.material_type,a.version_no DESC"
    )
    List<ContractAttachmentVO> attachments(Long contractId);

    /** 查询指定合同附件。 */
    @Select(
        "SELECT * FROM crm_attachment WHERE id=#{id} AND biz_type='CONTRACT' AND biz_id=#{contractId}"
    )
    ContractAttachment attachment(@Param("id") Long id, @Param("contractId") Long contractId);

    /** 将指定附件标记为核验通过。 */
    @Update(
        "UPDATE crm_attachment SET confirm_status=#{status},confirm_opinion=#{opinion},confirmed_by=#{operator},confirmed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND confirm_status='PENDING'"
    )
    int confirmAttachment(
        @Param("id") Long id,
        @Param("status") String status,
        @Param("opinion") String opinion,
        @Param("operator") Long operator,
        @Param("now") LocalDateTime now
    );

    /** 查询最新双方签署附件。 */
    @Select(
        "SELECT id FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type='SIGNED_CONTRACT' ORDER BY version_no DESC LIMIT 1"
    )
    Long latestSignedAttachment(Long contractId);

    /** 查询最新双方签署附件的核验状态。 */
    @Select(
        "SELECT confirm_status FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type='SIGNED_CONTRACT' ORDER BY version_no DESC LIMIT 1"
    )
    String latestSignedAttachmentStatus(Long contractId);

    /** 写入本次业务变更的审计快照。 */
    @Insert(
        "INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,before_json,after_json,reason) VALUES('CONTRACT',#{id},#{action},#{actor},#{team},#{now},#{before},#{after},#{reason})"
    )
    int audit(
        @Param("id") Long id,
        @Param("action") String action,
        @Param("actor") Long actor,
        @Param("team") Long team,
        @Param("now") LocalDateTime now,
        @Param("before") String before,
        @Param("after") String after,
        @Param("reason") String reason
    );

    /** 查询业务操作审计历史。 */
    @Select(
        "SELECT l.id,l.action_code actionCode,l.operator_id operatorId,COALESCE(NULLIF(u.real_name,''),u.username,CASE WHEN l.operator_id IS NULL THEN '系统任务' ELSE '历史账号' END) operatorName,l.occurred_at occurredAt,l.before_json beforeJson,l.after_json afterJson,l.reason FROM crm_operation_log l LEFT JOIN sys_user u ON u.id=l.operator_id WHERE l.biz_type='CONTRACT' AND l.biz_id=#{id} ORDER BY l.id DESC"
    )
    List<java.util.Map<String, Object>> operationHistory(Long id);

    /** 记录商机的签约确认时间。 */
    @Update(
        "UPDATE crm_opportunity SET signed_confirmed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{opportunityId} AND stage_code='DEAL' AND version=#{version}"
    )
    int markOpportunitySigned(
        @Param("opportunityId") Long opportunityId,
        @Param("version") Long version,
        @Param("now") LocalDateTime now
    );

    /** 将已通过合同核验的客户标记为保护中。 */
    @Update(
        "UPDATE crm_customer c SET customer_status='PROTECTED',version=version+1,updated_at=#{now} WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE' AND c.customer_status NOT IN ('DEAL','DEAL_PENDING')"
    )
    int protectCustomer(@Param("customerId") Long customerId, @Param("now") LocalDateTime now);
}
