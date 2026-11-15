package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.common.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查：后端活着没、数据库通不通、当前用的是哪个库。
 *
 * <pre>
 * GET /api/health
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "cloudmirror");
        data.put("version", "0.1.0");
        data.put("time", LocalDateTime.now().format(TIME));
        data.put("traceId", MDC.get(TraceIdFilter.TRACE_ID));

        long start = System.currentTimeMillis();
        try {
            jdbc.queryForObject("select 1", Integer.class);
            data.put("database", "up");
            data.put("databaseProduct", productName());
            data.put("databaseLatencyMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            data.put("database", "down");
            data.put("databaseError", rootMessage(e));
        }
        return Result.ok(data);
    }

    private String productName() {
        try (Connection conn = jdbc.getDataSource().getConnection()) {
            var meta = conn.getMetaData();
            return meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}
