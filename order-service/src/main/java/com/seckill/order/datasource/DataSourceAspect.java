package com.seckill.order.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Order(1)
public class DataSourceAspect {

    private static final Logger log = LoggerFactory.getLogger(DataSourceAspect.class);

    private static final String[] READ_PREFIXES =
            {"get", "find", "list", "query", "count", "select", "fetch", "load", "search"};

    @Around("execution(* com.seckill.order.service..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        DataSourceType type = resolveDataSourceType(pjp);
        DataSourceContextHolder.set(type);
        try {
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    private DataSourceType resolveDataSourceType(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        DS methodDS = method.getAnnotation(DS.class);
        if (methodDS != null) return methodDS.value();
        DS classDS = pjp.getTarget().getClass().getAnnotation(DS.class);
        if (classDS != null) return classDS.value();
        String methodName = method.getName();
        for (String prefix : READ_PREFIXES) {
            if (methodName.startsWith(prefix)) return DataSourceType.SLAVE;
        }
        return DataSourceType.MASTER;
    }
}
