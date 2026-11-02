/* 文件作用：管理员同团队批量交接的预览和确认参数。 */
package com.bishe.crm.module.customer.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class HandoverRequests {
    private HandoverRequests() {}
    public record Selection(@NotNull @Positive Long fromOwnerId,@NotNull @Positive Long toOwnerId,
        @NotEmpty @Size(max=50) List<@NotNull @Positive Long> customerIds) {}
    public record Execute(@NotNull @jakarta.validation.Valid Selection selection,
        @NotBlank @Size(max=64) String previewToken,@NotBlank @Size(max=500) String reason) {}
}
