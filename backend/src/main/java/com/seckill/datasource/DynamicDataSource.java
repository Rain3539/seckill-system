package com.seckill.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 动态数据源路由
 * <p>继承 Spring 的 {@link AbstractRoutingDataSource}，
 * 根据当前线程上下文选择主库或从库。</p>
 */
public class DynamicDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContextHolder.get();
        // 未显式设置时默认走主库，保证写操作安全
        DataSourceType resolved = (type != null) ? type : DataSourceType.MASTER;
        log.debug("[DataSource] 路由到数据源: {}", resolved);
        return resolved;
    }
}
