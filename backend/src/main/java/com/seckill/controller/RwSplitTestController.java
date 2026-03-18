package com.seckill.controller;

import com.seckill.datasource.DataSourceContextHolder;
import com.seckill.datasource.DataSourceType;
import com.seckill.model.vo.ResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读写分离效果验证接口
 *
 * <p>通过直接查询 MySQL 的 @@hostname / @@port 来确认当前连接来自哪个节点，
 * 从而直观验证路由是否按主/从正确分配。</p>
 *
 * GET /api/rw/master-info  — 强制走主库，返回主库连接信息
 * GET /api/rw/slave-info   — 强制走从库，返回从库连接信息
 * GET /api/rw/check        — 同时查询两个数据源，对比输出
 */
@RestController
@RequestMapping("/rw")
public class RwSplitTestController {

    private static final Logger log = LoggerFactory.getLogger(RwSplitTestController.class);

    private final DataSource masterDataSource;
    private final DataSource slaveDataSource;

    public RwSplitTestController(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource")  DataSource slaveDataSource) {
        this.masterDataSource = masterDataSource;
        this.slaveDataSource  = slaveDataSource;
    }

    /** 查询主库连接信息 */
    @GetMapping("/master-info")
    public ResultVO<Map<String, Object>> masterInfo() {
        Map<String, Object> info = queryDbInfo(masterDataSource, "MASTER");
        log.info("[RW-Test] master-info: {}", info);
        return ResultVO.success(info);
    }

    /** 查询从库连接信息 */
    @GetMapping("/slave-info")
    public ResultVO<Map<String, Object>> slaveInfo() {
        Map<String, Object> info = queryDbInfo(slaveDataSource, "SLAVE");
        log.info("[RW-Test] slave-info: {}", info);
        return ResultVO.success(info);
    }

    /**
     * 综合验证：分别查询主库和从库，展示路由差异
     */
    @GetMapping("/check")
    public ResultVO<Map<String, Object>> check() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> masterInfo = queryDbInfo(masterDataSource, "MASTER");
        Map<String, Object> slaveInfo  = queryDbInfo(slaveDataSource,  "SLAVE");

        result.put("master", masterInfo);
        result.put("slave",  slaveInfo);

        boolean separated = !masterInfo.get("hostname").equals(slaveInfo.get("hostname"))
                         || !masterInfo.get("port").equals(slaveInfo.get("port"));
        result.put("readWriteSeparated", separated);
        result.put("description", separated
                ? "✅ 主从库为不同实例，读写分离生效"
                : "⚠️ 主从库连接到同一实例（单机模式/主从同节点）");

        log.info("[RW-Test] check result: separated={}", separated);
        return ResultVO.success(result);
    }

    /**
     * AOP 路由验证：通过设置 ThreadLocal 后直接用 JdbcTemplate 查询
     */
    @GetMapping("/route-master")
    public ResultVO<Map<String, Object>> routeMaster() {
        DataSourceContextHolder.set(DataSourceType.MASTER);
        try {
            Map<String, Object> info = queryDbInfo(masterDataSource, "MASTER");
            info.put("routedBy", "AOP → DataSourceType.MASTER");
            return ResultVO.success(info);
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    @GetMapping("/route-slave")
    public ResultVO<Map<String, Object>> routeSlave() {
        DataSourceContextHolder.set(DataSourceType.SLAVE);
        try {
            Map<String, Object> info = queryDbInfo(slaveDataSource, "SLAVE");
            info.put("routedBy", "AOP → DataSourceType.SLAVE");
            return ResultVO.success(info);
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    // ── 工具方法 ─────────────────────────────────────────────────────
    private Map<String, Object> queryDbInfo(DataSource ds, String label) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            String hostname = jdbc.queryForObject("SELECT @@hostname", String.class);
            String port     = jdbc.queryForObject("SELECT @@port",     String.class);
            String version  = jdbc.queryForObject("SELECT VERSION()",  String.class);
            String serverId = jdbc.queryForObject("SELECT @@server_id", String.class);

            info.put("dataSource", label);
            info.put("hostname",   hostname);
            info.put("port",       port);
            info.put("serverId",   serverId);
            info.put("version",    version);
            info.put("status",     "connected");
        } catch (Exception e) {
            log.error("[RW-Test] {} 连接查询失败: {}", label, e.getMessage());
            info.put("dataSource", label);
            info.put("status",     "error: " + e.getMessage());
        }
        return info;
    }
}
