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

    @Min(value = 10, message = "每页固定 10 条")
    @Max(value = 10, message = "每页固定 10 条")
    private long size = 10;

    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }
}
