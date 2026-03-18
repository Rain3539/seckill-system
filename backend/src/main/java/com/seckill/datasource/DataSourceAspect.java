package com.seckill.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 读写分离 AOP 切面
 *
 * <p>拦截所有 Service 方法，按如下优先级决定数据源：
 * <ol>
 *   <li>方法上的 {@link DS} 注解</li>
 *   <li>类上的 {@link DS} 注解</li>
 *   <li>约定优于配置：方法名以 get/find/list/query/count/select 开头 → 从库</li>
 *   <li>兜底：主库</li>
 * </ol>
 *
 * <p>{@code @Order(1)} 保证在事务切面（Order=Integer.MAX_VALUE）之前执行，
 * 使同一事务内所有 SQL 都使用同一个连接，避免切库引起的事务失效。
 */
@Aspect
@Component
@Order(1)
public class DataSourceAspect {

    private static final Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    private static final String[] READ_PREFIXES =
            {"get", "find", "list", "query", "count", "select", "fetch", "load", "search"};

    @Around("execution(* com.seckill.service..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        DataSourceType type = resolveDataSourceType(pjp);
        DataSourceContextHolder.set(type);
        log.debug("[RW-Split] {}.{} → {}",
                pjp.getTarget().getClass().getSimpleName(),
                ((MethodSignature) pjp.getSignature()).getMethod().getName(),
                type);
        try {
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    private DataSourceType resolveDataSourceType(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();

        // 1. 方法注解优先
        DS methodDS = method.getAnnotation(DS.class);
        if (methodDS != null) return methodDS.value();

        // 2. 类注解次之
        DS classDS = pjp.getTarget().getClass().getAnnotation(DS.class);
        if (classDS != null) return classDS.value();

        // 3. 约定：读前缀 → SLAVE
        String methodName = method.getName();
        for (String prefix : READ_PREFIXES) {
            if (methodName.startsWith(prefix)) {
                return DataSourceType.SLAVE;
            }
        }

        // 4. 兜底：MASTER
        return DataSourceType.MASTER;
    }
}
