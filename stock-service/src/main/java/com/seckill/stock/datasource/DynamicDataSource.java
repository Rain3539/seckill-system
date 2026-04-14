package com.seckill.stock.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        DataSourceType type = DataSourceContextHolder.get();
        DataSourceType resolved = (type != null) ? type : DataSourceType.MASTER;
        log.debug("[DataSource] 路由到数据源: {}", resolved);
        return resolved;
    }
}
