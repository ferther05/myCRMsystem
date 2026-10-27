package com.bishe.crm.module.contract.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.auth.security.DataScope;
import com.bishe.crm.module.contract.dto.ContractQuery;
import com.bishe.crm.module.contract.entity.Contract;
import com.bishe.crm.module.contract.entity.ContractAttachment;
import com.bishe.crm.module.contract.vo.ContractAttachmentVO;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import com.bishe.crm.module.contract.vo.ContractRegistrationVO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ContractMapper {
    String SCOPE = "<choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<when test=\"s.role == 'MANAGER'\">c.team_id=#{s.teamId}</when>"
            + "<otherwise>c.owner_id=#{s.userId}</otherwise></choose>";
    String FIELDS = "SELECT c.id,c.contract_no,c.external_contract_no,c.customer_id,cu.customer_name,c.opportunity_id,o.opportunity_name,"
            + "c.owner_id,u.real_name owner_name,c.team_id,t.team_name,c.contract_name,c.business_type,c.our_party_name,c.counterparty_name,"
            + "c.contract_amount,c.signed_at,c.expire_date,c.contract_status,c.signing_progress,c.payment_status,c.received_amount,c.outstanding_amount,"
            + "c.version,c.created_at,c.updated_at FROM crm_contract c JOIN crm_customer cu ON cu.id=c.customer_id "
            + "JOIN crm_opportunity o ON o.id=c.opportunity_id JOIN sys_user u ON u.id=c.owner_id JOIN sys_team t ON t.id=c.team_id ";

    @Select("<script>" + FIELDS + "WHERE " + SCOPE
            + "<if test=\"q.keyword != null and q.keyword != ''\"> AND (c.contract_name LIKE CONCAT('%',#{q.keyword},'%') OR cu.customer_name LIKE CONCAT('%',#{q.keyword},'%') OR c.contract_no LIKE CONCAT('%',#{q.keyword},'%'))</if>"
            + "<if test=\"q.contractStatus != null and q.contractStatus != ''\"> AND c.contract_status=#{q.contractStatus}</if>"
            + "<if test=\"q.signingProgress != null and q.signingProgress != ''\"> AND c.signing_progress=#{q.signingProgress}</if>"
            + " ORDER BY c.id DESC</script>")
    IPage<ContractSummaryVO> list(Page<ContractSummaryVO> page, @Param("s") DataScope scope, @Param("q") ContractQuery q);

    @Select("<script>SELECT o.id opportunity_id,o.opportunity_name,o.customer_id,cu.customer_name,"
            + "o.owner_id,u.real_name owner_name,o.team_id,t.team_name,o.expected_amount,o.expected_close_date,"
            + "c.id contract_id,c.contract_no,c.contract_status,c.signing_progress "
            + "FROM crm_opportunity o JOIN crm_customer cu ON cu.id=o.customer_id AND cu.deleted=0 "
            + "JOIN sys_user u ON u.id=o.owner_id JOIN sys_team t ON t.id=o.team_id "
            + "LEFT JOIN crm_contract c ON c.id=(SELECT MAX(cc.id) FROM crm_contract cc WHERE cc.opportunity_id=o.id AND cc.contract_status != 'VOID') "
            + "WHERE o.stage_code='DEAL' AND <choose><when test=\"s.role == 'ADMIN'\">1=1</when>"
            + "<when test=\"s.role == 'MANAGER'\">o.team_id=#{s.teamId}</when><otherwise>o.owner_id=#{s.userId}</otherwise></choose>"
            + "<if test=\"q.keyword != null and q.keyword != ''\"> AND (o.opportunity_name LIKE CONCAT('%',#{q.keyword},'%') OR cu.customer_name LIKE CONCAT('%',#{q.keyword},'%') OR c.contract_no LIKE CONCAT('%',#{q.keyword},'%'))</if>"
            + "<if test=\"q.contractStatus == 'UNREGISTERED'\"> AND c.id IS NULL</if>"
            + "<if test=\"q.contractStatus != null and q.contractStatus != '' and q.contractStatus != 'UNREGISTERED'\"> AND c.contract_status=#{q.contractStatus}</if>"
            + "<if test=\"q.signingProgress != null and q.signingProgress != ''\"> AND c.signing_progress=#{q.signingProgress}</if>"
            + " ORDER BY o.id DESC</script>")
    IPage<ContractRegistrationVO> registrationOpportunities(Page<ContractRegistrationVO> page, @Param("s") DataScope scope, @Param("q") ContractQuery q);

    @Select("<script>" + FIELDS + "WHERE c.id=#{id} AND " + SCOPE + "</script>")
    ContractSummaryVO summary(@Param("id") Long id, @Param("s") DataScope scope);

    @Select("SELECT * FROM crm_contract WHERE id=#{id} FOR UPDATE")
    Contract lock(Long id);

    @Select("SELECT * FROM crm_contract WHERE id=#{id}")
    Contract find(Long id);

    @Select("SELECT * FROM crm_contract WHERE opportunity_id=#{opportunityId} AND contract_status <> 'VOID' ORDER BY id DESC LIMIT 1 FOR UPDATE")
    Contract currentByOpportunity(Long opportunityId);

    @Insert("INSERT INTO crm_contract(contract_no,external_contract_no,customer_id,opportunity_id,owner_id,team_id,contract_name,business_type,our_party_name,counterparty_name,description,contract_amount,contract_status,signing_progress,payment_status,received_amount,outstanding_amount,version,created_by,created_at,updated_at) "
            + "VALUES(#{c.contractNo},#{c.externalContractNo},#{c.customerId},#{c.opportunityId},#{c.ownerId},#{c.teamId},#{c.contractName},#{c.businessType},#{c.ourPartyName},#{c.counterpartyName},#{c.description},#{c.contractAmount},'DRAFT','INTERNAL_HANDLING','UNPAID',0,#{c.outstandingAmount},0,#{c.createdBy},#{c.createdAt},#{c.updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "c.id")
    int insert(@Param("c") Contract contract);

    @Update("UPDATE crm_contract SET contract_name=#{c.contractName},external_contract_no=#{c.externalContractNo},business_type=#{c.businessType},our_party_name=#{c.ourPartyName},counterparty_name=#{c.counterpartyName},contract_amount=#{c.contractAmount},signed_at=#{c.signedAt},expire_date=#{c.expireDate},description=#{c.description},outstanding_amount=#{c.contractAmount}-received_amount,version=version+1,updated_at=#{now} WHERE id=#{c.id} AND version=#{c.version} AND contract_status IN ('DRAFT','RETURNED')")
    int updateDraft(@Param("c") Contract contract, @Param("now") LocalDateTime now);

    @Update("UPDATE crm_contract SET contract_status=#{status},signed_at=COALESCE(#{signedAt},signed_at),version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status=#{expected}")
    int updateStatus(@Param("id") Long id,@Param("version") Long version,@Param("expected") String expected,@Param("status") String status,@Param("signedAt") java.time.LocalDate signedAt,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_contract SET signing_progress=#{progress},contract_status=CASE WHEN #{progress}='WITHDRAWN' THEN 'VOID' ELSE contract_status END,version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status IN ('DRAFT','RETURNED') AND signing_progress=#{expectedProgress}")
    int updateSigningProgress(@Param("id") Long id,@Param("version") Long version,@Param("expectedProgress") String expectedProgress,@Param("progress") String progress,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_contract SET contract_status='PENDING_REVIEW',version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status='DRAFT' AND signing_progress='WAIT_COUNTERPARTY_SIGN'")
    int submitSignedForReview(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_contract SET contract_status='PENDING_REVIEW',version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status='RETURNED' AND signing_progress='WAIT_COUNTERPARTY_SIGN'")
    int resubmitReturned(@Param("id") Long id,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_contract SET contract_status='SIGNED',signing_progress='BOTH_SIGNED',signed_at=COALESCE(#{signedAt},signed_at),version=version+1,updated_at=#{now} WHERE id=#{id} AND version=#{version} AND contract_status='PENDING_REVIEW' AND signing_progress='WAIT_COUNTERPARTY_SIGN'")
    int approveSignedForReview(@Param("id") Long id,@Param("version") Long version,@Param("signedAt") java.time.LocalDate signedAt,@Param("now") LocalDateTime now);

    @Insert("INSERT INTO crm_contract_signing_history(contract_id,from_progress,to_progress,operator_id,operator_name,operator_role,operator_team_id,changed_at,reason,is_correction) VALUES(#{id},#{from},#{to},#{actorId},#{actorName},#{role},#{teamId},#{now},#{reason},#{correction})")
    int insertSigningHistory(@Param("id") Long id,@Param("from") String from,@Param("to") String to,@Param("actorId") Long actorId,@Param("actorName") String actorName,@Param("role") String role,@Param("teamId") Long teamId,@Param("now") LocalDateTime now,@Param("reason") String reason,@Param("correction") boolean correction);

    @Select("SELECT h.id,h.from_progress fromProgress,h.to_progress toProgress,h.operator_name operatorName,h.operator_role operatorRole,h.changed_at changedAt,h.reason,h.is_correction correction FROM crm_contract_signing_history h WHERE h.contract_id=#{id} ORDER BY h.id DESC")
    List<java.util.Map<String,Object>> signingHistory(Long id);

    @Select("SELECT COUNT(*) FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{id} AND material_type='SIGNED_CONTRACT'")
    long signedAttachmentCount(Long id);

    @Select("SELECT COALESCE(MAX(version_no),0)+1 FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type=#{materialType}")
    int nextAttachmentVersion(@Param("contractId") Long contractId,@Param("materialType") String materialType);

    @Insert("INSERT INTO crm_attachment(biz_type,biz_id,material_type,version_no,file_name,storage_path,file_size,uploaded_by,uploaded_at,credential_note,confirm_status,version,created_at,updated_at) VALUES('CONTRACT',#{a.bizId},#{a.materialType},#{a.versionNo},#{a.fileName},#{a.storagePath},#{a.fileSize},#{a.uploadedBy},#{a.uploadedAt},#{a.credentialNote},'PENDING',0,#{a.uploadedAt},#{a.uploadedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "a.id")
    int insertAttachment(@Param("a") ContractAttachment attachment);

    @Select("SELECT a.id,a.material_type materialType,a.version_no versionNo,a.file_name fileName,a.file_size fileSize,a.uploaded_by uploadedBy,up.real_name uploaderName,a.uploaded_at uploadedAt,a.credential_note credentialNote,a.confirm_status confirmStatus,a.confirm_opinion confirmOpinion,a.confirmed_by confirmedBy,cp.real_name confirmerName,a.confirmed_at confirmedAt FROM crm_attachment a JOIN sys_user up ON up.id=a.uploaded_by LEFT JOIN sys_user cp ON cp.id=a.confirmed_by WHERE a.biz_type='CONTRACT' AND a.biz_id=#{contractId} ORDER BY a.material_type,a.version_no DESC")
    List<ContractAttachmentVO> attachments(Long contractId);

    @Select("SELECT * FROM crm_attachment WHERE id=#{id} AND biz_type='CONTRACT' AND biz_id=#{contractId}")
    ContractAttachment attachment(@Param("id") Long id,@Param("contractId") Long contractId);

    @Update("UPDATE crm_attachment SET confirm_status=#{status},confirm_opinion=#{opinion},confirmed_by=#{operator},confirmed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{id} AND confirm_status='PENDING'")
    int confirmAttachment(@Param("id") Long id,@Param("status") String status,@Param("opinion") String opinion,@Param("operator") Long operator,@Param("now") LocalDateTime now);

    @Select("SELECT id FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type='SIGNED_CONTRACT' ORDER BY version_no DESC LIMIT 1")
    Long latestSignedAttachment(Long contractId);

    @Select("SELECT confirm_status FROM crm_attachment WHERE biz_type='CONTRACT' AND biz_id=#{contractId} AND material_type='SIGNED_CONTRACT' ORDER BY version_no DESC LIMIT 1")
    String latestSignedAttachmentStatus(Long contractId);

    @Insert("INSERT INTO crm_operation_log(biz_type,biz_id,action_code,operator_id,operator_team_id,occurred_at,before_json,after_json,reason) VALUES('CONTRACT',#{id},#{action},#{actor},#{team},#{now},#{before},#{after},#{reason})")
    int audit(@Param("id") Long id,@Param("action") String action,@Param("actor") Long actor,@Param("team") Long team,@Param("now") LocalDateTime now,@Param("before") String before,@Param("after") String after,@Param("reason") String reason);

    @Select("SELECT id,action_code actionCode,operator_id operatorId,occurred_at occurredAt,before_json beforeJson,after_json afterJson,reason FROM crm_operation_log WHERE biz_type='CONTRACT' AND biz_id=#{id} ORDER BY id DESC")
    List<java.util.Map<String,Object>> operationHistory(Long id);

    @Update("UPDATE crm_opportunity SET signed_confirmed_at=#{now},version=version+1,updated_at=#{now} WHERE id=#{opportunityId} AND stage_code='DEAL' AND version=#{version}")
    int markOpportunitySigned(@Param("opportunityId") Long opportunityId,@Param("version") Long version,@Param("now") LocalDateTime now);

    @Update("UPDATE crm_customer c SET customer_status='PROTECTED',version=version+1,updated_at=#{now} WHERE c.id=#{customerId} AND c.deleted=0 AND c.sea_type='PRIVATE'")
    int protectCustomer(@Param("customerId") Long customerId,@Param("now") LocalDateTime now);
}
