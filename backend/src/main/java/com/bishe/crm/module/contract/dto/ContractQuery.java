/* 文件作用：合同列表的分页与筛选条件。 */
package com.bishe.crm.module.contract.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/** 合同列表的分页与筛选条件。 */
@Getter
@Setter
public class ContractQuery {

    @Min(1)
    private long page = 1;

    @Min(value = 10, message = "每页固定 10 条")
    @Max(value = 10, message = "每页固定 10 条")
    private long size = 10;

    @jakarta.validation.constraints.Positive
    private Long opportunityId;

    private String keyword;
    private String contractStatus;
    private String signingProgress;

    /** 生成固定每页十条的分页查询对象。 */
    public Page<ContractSummaryVO> toPage() {
        return new Page<>(page, size);
    }

    /** 将空搜索词转为 null，避免无效筛选。 */
    public String keywordOrNull() {
        return Objects.requireNonNullElse(keyword, "").trim();
    }
}
