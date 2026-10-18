package com.bishe.crm.module.health.mapper;

import org.apache.ibatis.annotations.Select;

public interface DatabaseProbeMapper {
    @Select("SELECT 1")
    Integer ping();
}
