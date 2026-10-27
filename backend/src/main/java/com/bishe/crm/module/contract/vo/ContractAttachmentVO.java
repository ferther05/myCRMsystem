package com.bishe.crm.module.contract.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContractAttachmentVO {
    private Long id; private String materialType; private Integer versionNo; private String fileName;
    private Long fileSize; private Long uploadedBy; private String uploaderName; private LocalDateTime uploadedAt;
    private String credentialNote; private String confirmStatus; private String confirmOpinion;
    private Long confirmedBy; private String confirmerName; private LocalDateTime confirmedAt;
    private String downloadUrl;
}
