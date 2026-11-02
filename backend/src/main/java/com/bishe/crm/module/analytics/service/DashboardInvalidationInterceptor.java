/* 文件作用：业务Mapper写入提交后统一失效看板，回滚不触发失效。 */
package com.bishe.crm.module.analytics.service;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component @RequiredArgsConstructor
@Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
public class DashboardInvalidationInterceptor implements Interceptor {
    private final DashboardCache cache;
    /** 仅在影响行数大于零时安排提交后失效；无事务写入立即失效。 */
    @Override public Object intercept(Invocation invocation) throws Throwable {
        Object result=invocation.proceed();
        if(result instanceof Integer count && count>0) {
            if(TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {cache.invalidate();}
                });
            } else cache.invalidate();
        }
        return result;
    }
}
