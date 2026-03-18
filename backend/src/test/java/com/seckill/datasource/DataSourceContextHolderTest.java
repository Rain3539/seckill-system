package com.seckill.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSourceContextHolder 单元测试
 *
 * 验证：
 * 1. 默认无设置时 get() 返回 null（交由 DynamicDataSource 兜底选主库）
 * 2. set(MASTER) / set(SLAVE) 正确写入并可读取
 * 3. clear() 后恢复 null
 * 4. isSlave() 工具方法语义正确
 * 5. ThreadLocal 隔离：不同线程的数据源设置互不干扰
 */
@DisplayName("DataSourceContextHolder 线程隔离测试")
class DataSourceContextHolderTest {

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("默认值为 null，交由 DynamicDataSource 兜底选主库")
    void defaultIsNull() {
        assertNull(DataSourceContextHolder.get(), "未设置时应为 null");
        assertFalse(DataSourceContextHolder.isSlave(), "未设置时 isSlave 应为 false");
    }

    @Test
    @DisplayName("set(MASTER) 后可正确读取")
    void setMaster() {
        DataSourceContextHolder.set(DataSourceType.MASTER);
        assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get());
        assertFalse(DataSourceContextHolder.isSlave());
    }

    @Test
    @DisplayName("set(SLAVE) 后可正确读取")
    void setSlave() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        assertEquals(DataSourceType.SLAVE, DataSourceContextHolder.get());
        assertTrue(DataSourceContextHolder.isSlave());
    }

    @Test
    @DisplayName("clear() 后恢复 null")
    void clearResetsToNull() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        DataSourceContextHolder.clear();
        assertNull(DataSourceContextHolder.get());
    }

    @Test
    @DisplayName("ThreadLocal 隔离：子线程修改不影响父线程")
    void threadIsolation() throws InterruptedException {
        // 父线程设置 MASTER
        DataSourceContextHolder.set(DataSourceType.MASTER);

        AtomicReference<DataSourceType> childResult = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread child = new Thread(() -> {
            // 子线程独立设置 SLAVE
            DataSourceContextHolder.set(DataSourceType.SLAVE);
            childResult.set(DataSourceContextHolder.get());
            DataSourceContextHolder.clear();
            latch.countDown();
        });
        child.start();
        latch.await();

        // 父线程的设置不受子线程影响
        assertEquals(DataSourceType.MASTER, DataSourceContextHolder.get(),
                "父线程数据源不应被子线程修改");
        assertEquals(DataSourceType.SLAVE, childResult.get(),
                "子线程应持有 SLAVE");
    }

    @Test
    @DisplayName("同一线程多次切换数据源")
    void multiSwitch() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        assertTrue(DataSourceContextHolder.isSlave());

        DataSourceContextHolder.set(DataSourceType.MASTER);
        assertFalse(DataSourceContextHolder.isSlave());

        DataSourceContextHolder.set(DataSourceType.SLAVE);
        assertTrue(DataSourceContextHolder.isSlave());
    }
}
