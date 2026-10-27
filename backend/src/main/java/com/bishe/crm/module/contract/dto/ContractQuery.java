package com.bishe.crm.module.contract.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Objects;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bishe.crm.module.contract.vo.ContractSummaryVO;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ContractQuery {
    @Min(1) private long page = 1;
    @Min(value=10, message="每页固定 10 条") @Max(value=10, message="每页固定 10 条") private long size = 10;
    private String keyword;
    private String contractStatus;
    private String signingProgress;

    public Page<ContractSummaryVO> toPage() { return new Page<>(page, size); }
    public String keywordOrNull() { return Objects.requireNonNullElse(keyword, "").trim(); }
}
