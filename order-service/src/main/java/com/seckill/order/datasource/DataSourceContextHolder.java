package com.seckill.order.datasource;

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
