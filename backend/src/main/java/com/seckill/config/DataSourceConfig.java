package com.seckill.config;

import com.seckill.datasource.DataSourceType;
import com.seckill.datasource.DynamicDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离数据源配置
 *
 * <p>构建两个 HikariCP 连接池：
 * <ul>
 *   <li>masterDataSource  — 主库，负责写操作</li>
 *   <li>slaveDataSource   — 从库，负责读操作</li>
 * </ul>
 * 再包装成 {@link DynamicDataSource}，注册为 Spring 的 @Primary DataSource。
 */
@Configuration
public class DataSourceConfig {

    /** 主库连接池 */
    @Bean("masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /** 从库连接池 */
    @Bean("slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * 动态路由数据源（@Primary，替换 Spring Boot 自动配置的单数据源）
     */
    @Primary
    @Bean("dynamicDataSource")
    public DataSource dynamicDataSource(
            @Qualifier("masterDataSource") DataSource master,
            @Qualifier("slaveDataSource")  DataSource slave) {

        DynamicDataSource ds = new DynamicDataSource();

        Map<Object, Object> targetMap = new HashMap<>();
        targetMap.put(DataSourceType.MASTER, master);
        targetMap.put(DataSourceType.SLAVE,  slave);

        ds.setTargetDataSources(targetMap);
        ds.setDefaultTargetDataSource(master); // 未命中 key 时回退到主库
        return ds;
    }
}
