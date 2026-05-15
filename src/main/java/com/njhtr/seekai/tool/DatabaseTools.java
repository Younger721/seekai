package com.njhtr.seekai.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 数据库分析工具 (供 DataAgent 使用)
 * 提供：获取表结构、执行安全 SQL 查询等核心能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseTools {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    // 严禁执行带有破坏性的关键字
    private static final Pattern UNSAFE_SQL_PATTERN = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|GRANT|REVOKE|REPLACE)\\b"
    );

    /**
     * 获取数据库 Schema 工具
     * 返回所有表名及其包含的字段名称、类型、注释等，供大模型生成准确的 SQL 语句。
     */
    public Function<SchemaRequest, SchemaResponse> getDatabaseSchema() {
        return request -> {
            log.info("📊 [DataAgent] 请求获取数据库表结构 Schema...");
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schemaPattern = null;
                
                // 获取所有的表
                ResultSet tables = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE", "VIEW"});
                List<TableInfo> tableList = new ArrayList<>();
                
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableRemarks = tables.getString("REMARKS");
                    
                    // 为了防止把系统内部表(如 SpringAI 的向量表、Flyway 的历史表)喂给模型造成干扰，可以选择性过滤
                    // 但由于这里你想查询 spring_ai_chat_memory 作为测试，所以暂时放开
                    if (tableName.toLowerCase().startsWith("sys_") || 
                        tableName.toLowerCase().equals("flyway_schema_history")) {
                        continue;
                    }
                    
                    TableInfo tableInfo = new TableInfo(tableName, tableRemarks, new ArrayList<>());
                    
                    // 获取当前表的字段信息
                    ResultSet columns = metaData.getColumns(catalog, schemaPattern, tableName, "%");
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String columnType = columns.getString("TYPE_NAME");
                        String columnRemarks = columns.getString("REMARKS");
                        tableInfo.columns().add(new ColumnInfo(columnName, columnType, columnRemarks));
                    }
                    tableList.add(tableInfo);
                }
                
                log.info("✅ 成功获取 {} 张表的结构信息", tableList.size());
                return new SchemaResponse(true, "成功获取表结构", tableList);
                
            } catch (Exception e) {
                log.error("❌ 获取数据库表结构失败：{}", e.getMessage(), e);
                return new SchemaResponse(false, "Error: " + e.getMessage(), new ArrayList<>());
            }
        };
    }

    /**
     * 执行自然语言生成的 SQL 查询
     * 必须进行严格的安全拦截，只允许执行 SELECT 语句，防止数据被破坏。
     */
    public Function<ExecuteSqlRequest, ExecuteSqlResponse> executeSQL() {
        return request -> {
            String sql = request.sql();
            log.warn("⚠️ [DataAgent] 请求执行动态 SQL 查询：\n{}", sql);
            
            try {
                if (sql == null || sql.trim().isEmpty()) {
                    return new ExecuteSqlResponse(false, "SQL 不能为空", null);
                }
                
                // 1. 安全拦截：检查是否存在破坏性关键字
                if (UNSAFE_SQL_PATTERN.matcher(sql).find()) {
                    log.error("⛔ 安全拦截：检测到破坏性 SQL -> {}", sql);
                    return new ExecuteSqlResponse(false, "Error: 你的操作权限仅限数据分析！严禁执行 INSERT/UPDATE/DELETE/DROP/ALTER 等修改操作！请只使用纯 SELECT 语句。", null);
                }
                
                // 2. 限制查询数量，防止结果集撑爆大模型 Token
                if (!sql.toLowerCase().contains("limit")) {
                    sql = sql + " LIMIT 50"; // 默认只给大模型看前 50 条数据
                }
                
                // 3. 执行查询
                List<Map<String, Object>> resultList = jdbcTemplate.queryForList(sql);
                log.info("✅ SQL 执行成功，共返回 {} 条记录", resultList.size());
                
                // 4. 防止记录太多依然撑爆 Token
                if (resultList.size() > 100) {
                    List<Map<String, Object>> truncatedList = resultList.subList(0, 100);
                    return new ExecuteSqlResponse(true, "执行成功，返回了 " + resultList.size() + " 条记录，为保护上下文，只展示前 100 条。", truncatedList);
                }
                
                return new ExecuteSqlResponse(true, "执行成功，返回了 " + resultList.size() + " 条记录。", resultList);
                
            } catch (Exception e) {
                log.error("❌ 动态 SQL 执行报错：{}", e.getMessage());
                // 把报错原因返回给大模型，让它知道自己哪里写错了（自愈合闭环）
                return new ExecuteSqlResponse(false, "SQL执行异常，请检查语法或字段名是否存在: " + e.getMessage(), null);
            }
        };
    }

    // -- 请求与响应记录类 --
    
    public record SchemaRequest() {}
    
    public record ColumnInfo(String name, String type, String comment) {}
    
    public record TableInfo(String tableName, String tableComment, List<ColumnInfo> columns) {}
    
    public record SchemaResponse(boolean success, String message, List<TableInfo> tables) {}

    public record ExecuteSqlRequest(String sql) {}

    public record ExecuteSqlResponse(boolean success, String message, List<Map<String, Object>> data) {}

}