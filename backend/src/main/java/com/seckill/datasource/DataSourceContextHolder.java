package com.seckill.datasource;

/**
 * 数据源上下文持有器（基于 ThreadLocal）
 * <p>每个请求线程独立持有当前应使用的数据源类型，AOP 切入时写入，
 * 请求结束后必须调用 {@link #clear()} 防止内存泄漏。</p>
 */
public class DataSourceContextHolder {

    private static final ThreadLocal<DataSourceType> CONTEXT =
            new InheritableThreadLocal<>();

    private DataSourceContextHolder() {}

    public static void set(DataSourceType type) {
        CONTEXT.set(type);
    }

    public static DataSourceType get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean isSlave() {
        return DataSourceType.SLAVE == CONTEXT.get();
    }
}
