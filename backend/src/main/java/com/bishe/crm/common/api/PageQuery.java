package com.bishe.crm.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {
    @Min(value = 1, message = "页码至少为 1")
    private long page = 1;

    @Min(value = 1, message = "每页至少 1 条")
    @Max(value = 100, message = "每页最多 100 条")
    private long size = 20;

    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }
}
