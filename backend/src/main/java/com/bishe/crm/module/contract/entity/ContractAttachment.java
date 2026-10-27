package com.bishe.crm.module.contract.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @TableName("crm_attachment")
public class ContractAttachment {
    @TableId(type = IdType.AUTO) private Long id;
    private String bizType;
    private Long bizId;
    private String materialType;
    private Integer versionNo;
    private String fileName;
    private String storagePath;
    private Long fileSize;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private String credentialNote;
    private String confirmStatus;
    private String confirmOpinion;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long version;
}
