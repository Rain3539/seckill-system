package com.seckill.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicDataSource 路由逻辑测试
 *
 * 不依赖真实数据库，用 Mockito Mock DataSource。
 * 验证：
 * 1. ThreadLocal 为空时路由到 MASTER（兜底）
 * 2. set(SLAVE) 时路由到 SLAVE
 * 3. set(MASTER) 时路由到 MASTER
 * 4. determineCurrentLookupKey() 返回值与 ThreadLocal 一致
 */
@DisplayName("DynamicDataSource 路由键测试")
class DynamicDataSourceTest {

    private DynamicDataSource dynamicDataSource;
    private DataSource        mockMaster;
    private DataSource        mockSlave;

    @BeforeEach
    void setUp() {
        mockMaster = Mockito.mock(DataSource.class);
        mockSlave  = Mockito.mock(DataSource.class);

        dynamicDataSource = new DynamicDataSource();

        Map<Object, Object> targetMap = new HashMap<>();
        targetMap.put(DataSourceType.MASTER, mockMaster);
        targetMap.put(DataSourceType.SLAVE,  mockSlave);

        dynamicDataSource.setTargetDataSources(targetMap);
        dynamicDataSource.setDefaultTargetDataSource(mockMaster);
        dynamicDataSource.afterPropertiesSet(); // 触发 resolvedDataSources 初始化
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("ThreadLocal 未设置时，路由键为 MASTER（兜底）")
    void defaultRoutesToMaster() {
        // 不调用 DataSourceContextHolder.set()
        Object key = invokeKey();
        assertEquals(DataSourceType.MASTER, key,
                "未显式设置时应路由到 MASTER");
    }

    @Test
    @DisplayName("set(SLAVE) 时，路由键为 SLAVE")
    void slaveModeRoutesToSlave() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        Object key = invokeKey();
        assertEquals(DataSourceType.SLAVE, key,
                "SLAVE 模式应路由到从库");
    }

    @Test
    @DisplayName("set(MASTER) 时，路由键为 MASTER")
    void masterModeRoutesToMaster() {
        DataSourceContextHolder.set(DataSourceType.MASTER);
        Object key = invokeKey();
        assertEquals(DataSourceType.MASTER, key,
                "MASTER 模式应路由到主库");
    }

    @Test
    @DisplayName("clear() 后再路由，重新回到 MASTER（兜底）")
    void afterClearRoutesToMaster() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        DataSourceContextHolder.clear();
        Object key = invokeKey();
        assertEquals(DataSourceType.MASTER, key,
                "clear 后应兜底到 MASTER");
    }

    // ── 反射调用 protected 方法 ─────────────────────────────────────
    private Object invokeKey() {
        try {
            var method = AbstractRoutingDataSource.class
                    .getDeclaredMethod("determineCurrentLookupKey");
            method.setAccessible(true);
            return method.invoke(dynamicDataSource);
        } catch (Exception e) {
            throw new RuntimeException("反射调用 determineCurrentLookupKey 失败", e);
        }
    }
}
