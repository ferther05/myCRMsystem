/* 文件作用：返回合同附件版本及可用操作信息。 */
package com.bishe.crm.module.contract.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 返回合同附件版本及可用操作信息。 */
@Getter
@Setter
public class ContractAttachmentVO {

    private Long id;
    private String materialType;
    private Integer versionNo;
    private String fileName;
    private Long fileSize;
    private Long uploadedBy;
    private String uploaderName;
    private LocalDateTime uploadedAt;
    private String credentialNote;
    private String confirmStatus;
    private String confirmOpinion;
    private Long confirmedBy;
    private String confirmerName;
    private LocalDateTime confirmedAt;
    private String downloadUrl;
}
