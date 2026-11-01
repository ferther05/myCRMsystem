/* 文件作用：配置合同附件的本地根目录，不向前端暴露本机路径。 */
package com.bishe.crm.module.contract.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 配置只供后端使用，不序列化为接口响应，也不输出凭据。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "crm.files")
public class ContractStorageProperties {
    private String root = "../output/contract-files";
}
