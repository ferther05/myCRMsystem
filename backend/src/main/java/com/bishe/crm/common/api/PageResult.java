/* 文件作用：分页响应，返回总数与当前页记录。 */
package com.bishe.crm.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

/** 分页响应，返回总数与当前页记录。 */
public record PageResult<T>(long total, List<T> list) {
    /** 转换为对外返回的数据对象。 */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getTotal(), List.copyOf(page.getRecords()));
    }
}
