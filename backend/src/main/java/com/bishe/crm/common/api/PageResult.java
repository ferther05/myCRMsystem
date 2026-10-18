package com.bishe.crm.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(long total, List<T> list) {

    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getTotal(), List.copyOf(page.getRecords()));
    }
}
