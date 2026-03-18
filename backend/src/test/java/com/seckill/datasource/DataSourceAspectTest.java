package com.seckill.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * DataSourceAspect 路由决策测试
 *
 * 验证切面在以下场景中正确决定数据源：
 * 1. 方法上有 @DS(SLAVE)  → SLAVE
 * 2. 方法上有 @DS(MASTER) → MASTER
 * 3. 无注解但方法名以 get/find/list 开头 → SLAVE（约定）
 * 4. 无注解且非读前缀 → MASTER（兜底）
 * 5. AOP 执行后 ThreadLocal 被清理（不泄漏）
 */
@DisplayName("DataSourceAspect 路由决策测试")
class DataSourceAspectTest {

    private final DataSourceAspect aspect = new DataSourceAspect();

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    // ── 辅助：模拟 ProceedingJoinPoint ──────────────────────────────
    private ProceedingJoinPoint mockPjp(Method method, Object target) {
        ProceedingJoinPoint pjp = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature sig     = Mockito.mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(target);
        return pjp;
    }

    // ── 测试目标类 ───────────────────────────────────────────────────
    static class FakeService {
        @DS(DataSourceType.SLAVE)
        public void findById() {}

        @DS(DataSourceType.MASTER)
        public void saveUser() {}

        public void listProducts() {}   // 约定：list 前缀 → SLAVE

        public void createOrder() {}    // 兜底 → MASTER

        public void getDetail() {}      // 约定：get 前缀 → SLAVE

        public void updateStatus() {}   // 兜底 → MASTER
    }

    @Test
    @DisplayName("方法注解 @DS(SLAVE) → 路由到从库")
    void annotationSlave() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("findById");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.SLAVE, result);
    }

    @Test
    @DisplayName("方法注解 @DS(MASTER) → 路由到主库")
    void annotationMaster() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("saveUser");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.MASTER, result);
    }

    @Test
    @DisplayName("无注解但 list 前缀 → 约定走从库")
    void conventionListSlave() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("listProducts");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.SLAVE, result);
    }

    @Test
    @DisplayName("无注解但 get 前缀 → 约定走从库")
    void conventionGetSlave() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("getDetail");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.SLAVE, result);
    }

    @Test
    @DisplayName("无注解且无读前缀 createOrder → 兜底走主库")
    void fallbackMaster() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("createOrder");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.MASTER, result);
    }

    @Test
    @DisplayName("update 前缀 → 兜底走主库")
    void updateMaster() throws NoSuchMethodException {
        Method m = FakeService.class.getMethod("updateStatus");
        var pjp  = mockPjp(m, new FakeService());
        DataSourceType result = invokeResolve(pjp);
        assertEquals(DataSourceType.MASTER, result);
    }

    @Test
    @DisplayName("AOP around 执行完毕后 ThreadLocal 被清理")
    void threadLocalClearedAfterAround() throws Throwable {
        Method m = FakeService.class.getMethod("listProducts");
        ProceedingJoinPoint pjp = mockPjp(m, new FakeService());
        when(pjp.proceed()).thenReturn(null);

        aspect.around(pjp);

        assertNull(DataSourceContextHolder.get(),
                "AOP 完成后 ThreadLocal 应被清理，避免连接池复用时污染");
    }

    // ── 反射调用 private resolveDataSourceType ──────────────────────
    private DataSourceType invokeResolve(ProceedingJoinPoint pjp) {
        try {
            var method = DataSourceAspect.class
                    .getDeclaredMethod("resolveDataSourceType", ProceedingJoinPoint.class);
            method.setAccessible(true);
            return (DataSourceType) method.invoke(aspect, pjp);
        } catch (Exception e) {
            throw new RuntimeException("反射调用 resolveDataSourceType 失败", e);
        }
    }
}
