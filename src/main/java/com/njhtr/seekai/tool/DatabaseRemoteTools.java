package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * 远程数据库连接工具 - 提供任意 MySQL 数据库的连接和操作
 * 支持通过参数指定 host、port、username、password 进行远程连接
 */
@Slf4j
@Component
public class DatabaseRemoteTools {

    /**
     * 测试远程数据库连接
     *
     * @param host     数据库主机地址
     * @param port     端口号（默认3306）
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名称
     * @return 连接测试结果
     */
    @Tool(description = "测试远程 MySQL 数据库连接是否成功。返回连接状态、数据库信息等。")
    public String testConnection(
            @ToolParam(description = "数据库主机地址或IP") String host,
            @ToolParam(description = "端口号，默认3306") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "数据库名称") String database) {

        log.info("🔗 测试远程数据库连接: {}@{}:{}/{}", username, host, port, database);

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("host", host);
            result.put("port", port);
            result.put("database", database);
            result.put("connected", conn.isValid(5));
            result.put("catalog", conn.getCatalog());
            result.put("driverVersion", conn.getMetaData().getDriverVersion());

            log.info("✅ 数据库连接成功");
            return toJson(result);

        } catch (SQLException e) {
            log.error("❌ 数据库连接失败: {}", e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "host", host,
                    "port", port,
                    "database", database,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 执行 SQL 查询
     *
     * @param host     数据库主机
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sql      SQL 查询语句
     * @return 查询结果
     */
    @Tool(description = "执行 SELECT 查询语句。返回查询结果的表格数据。注意：仅支持查询操作，不允许修改数据。")
    public String executeQuery(
            @ToolParam(description = "数据库主机地址或IP") String host,
            @ToolParam(description = "端口号，默认3306") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "SELECT 查询语句") String sql) {

        // 安全检查：只允许 SELECT 查询
        String trimmedSql = sql.trim().toLowerCase();
        if (!trimmedSql.startsWith("select")) {
            return toJson(Map.of(
                    "success", false,
                    "error", "只允许执行 SELECT 查询语句，不允许执行: " + trimmedSql.split(" ")[0]
            ));
        }

        log.info("🔍 执行查询: {}", sql);

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // 获取列信息
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            // 获取数据
            List<Map<String, Object>> rows = new ArrayList<>();
            int rowCount = 0;
            while (rs.next() && rowCount < 1000) {  // 限制返回行数
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    row.put(columns.get(i - 1), value);
                }
                rows.add(row);
                rowCount++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("sql", sql);
            result.put("columns", columns);
            result.put("rowCount", rows.size());
            result.put("rows", rows);

            log.info("✅ 查询成功，返回 {} 行", rows.size());
            return toJson(result);

        } catch (SQLException e) {
            log.error("❌ 查询失败: {}", e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "sql", sql,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取数据库表列表
     *
     * @param host     数据库主机
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @return 表列表
     */
    @Tool(description = "获取指定数据库中的所有表列表。返回表名、表类型、行数等信息。")
    public String listTables(
            @ToolParam(description = "数据库主机地址或IP") String host,
            @ToolParam(description = "端口号，默认3306") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "数据库名称") String database) {

        log.info("📋 获取数据库表列表: {}", database);

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            DatabaseMetaData metaData = conn.getMetaData();
            List<Map<String, Object>> tables = new ArrayList<>();

            try (ResultSet rs = metaData.getTables(database, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    Map<String, Object> table = new LinkedHashMap<>();
                    table.put("tableName", rs.getString("TABLE_NAME"));
                    table.put("tableType", rs.getString("TABLE_TYPE"));
                    table.put("remarks", rs.getString("REMARKS"));
                    tables.add(table);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("database", database);
            result.put("tableCount", tables.size());
            result.put("tables", tables);

            return toJson(result);

        } catch (SQLException e) {
            return toJson(Map.of(
                    "success", false,
                    "database", database,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取表结构信息
     *
     * @param host     数据库主机
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param tableName 表名
     * @return 表结构信息
     */
    @Tool(description = "获取指定表的结构信息，包括列名、数据类型、键信息、默认值等。")
    public String getTableSchema(
            @ToolParam(description = "数据库主机地址或IP") String host,
            @ToolParam(description = "端口号，默认3306") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "表名") String tableName) {

        log.info("📐 获取表结构: {}.{}", database, tableName);

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try (Connection conn = DriverManager.getConnection(url, username, password)) {

            DatabaseMetaData metaData = conn.getMetaData();
            List<Map<String, Object>> columns = new ArrayList<>();

            try (ResultSet rs = metaData.getColumns(database, null, tableName, null)) {
                while (rs.next()) {
                    Map<String, Object> column = new LinkedHashMap<>();
                    column.put("columnName", rs.getString("COLUMN_NAME"));
                    column.put("dataType", rs.getString("TYPE_NAME"));
                    column.put("columnSize", rs.getInt("COLUMN_SIZE"));
                    column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    column.put("columnDefault", rs.getString("COLUMN_DEF"));
                    column.put("remarks", rs.getString("REMARKS"));
                    columns.add(column);
                }
            }

            // 获取主键信息
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet rs = metaData.getPrimaryKeys(database, null, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("database", database);
            result.put("tableName", tableName);
            result.put("columnCount", columns.size());
            result.put("primaryKeys", primaryKeys);
            result.put("columns", columns);

            return toJson(result);

        } catch (SQLException e) {
            return toJson(Map.of(
                    "success", false,
                    "tableName", tableName,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 执行 DML 操作（INSERT/UPDATE/DELETE）
     *
     * @param host     数据库主机
     * @param port     端口
     * @param username 用户名
     * @param password 密码
     * @param database 数据库名
     * @param sql      DML 语句
     * @return 操作结果
     */
    @Tool(description = "执行 INSERT/UPDATE/DELETE 数据修改操作。返回影响的行数。注意：此操作会修改数据，请谨慎使用。")
    public String executeDML(
            @ToolParam(description = "数据库主机地址或IP") String host,
            @ToolParam(description = "端口号，默认3306") int port,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "数据库名称") String database,
            @ToolParam(description = "INSERT/UPDATE/DELETE 语句") String sql) {

        // 安全检查：只允许 DML 操作
        String trimmedSql = sql.trim().toLowerCase();
        if (!trimmedSql.startsWith("insert") && !trimmedSql.startsWith("update") && !trimmedSql.startsWith("delete")) {
            return toJson(Map.of(
                    "success", false,
                    "error", "只允许执行 INSERT/UPDATE/DELETE 操作"
            ));
        }

        log.info("✏️ 执行 DML: {}", sql);

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {

            int affectedRows = stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("sql", sql);
            result.put("affectedRows", affectedRows);

            // 获取自动生成的 ID
            List<Long> generatedIds = new ArrayList<>();
            if (trimmedSql.startsWith("insert")) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    while (rs.next()) {
                        generatedIds.add(rs.getLong(1));
                    }
                }
            }
            result.put("generatedIds", generatedIds);

            log.info("✅ DML 执行成功，影响 {} 行", affectedRows);
            return toJson(result);

        } catch (SQLException e) {
            log.error("❌ DML 执行失败: {}", e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "sql", sql,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 快速连接并查询（简化版）
     *
     * @param connectionString JDBC 连接字符串，如 "jdbc:mysql://localhost:3306/mydb"
     * @param username          用户名
     * @param password          密码
     * @param sql               SQL 查询
     * @return 查询结果
     */
    @Tool(description = "简化版查询：直接输入 JDBC 连接字符串、用户名、密码和 SQL，进行快速查询。")
    public String quickQuery(
            @ToolParam(description = "JDBC 连接字符串，如 jdbc:mysql://localhost:3306/mydb") String connectionString,
            @ToolParam(description = "数据库用户名") String username,
            @ToolParam(description = "数据库密码") String password,
            @ToolParam(description = "SELECT 查询语句") String sql) {

        // 安全检查
        String trimmedSql = sql.trim().toLowerCase();
        if (!trimmedSql.startsWith("select")) {
            return toJson(Map.of("success", false, "error", "只允许 SELECT 查询"));
        }

        log.info("🔍 快速查询: {}", connectionString);

        try (Connection conn = DriverManager.getConnection(connectionString, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columns.add(metaData.getColumnLabel(i));
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            int rowCount = 0;
            while (rs.next() && rowCount < 1000) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columns.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
                rowCount++;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("columns", columns);
            result.put("rowCount", rows.size());
            result.put("rows", rows);

            return toJson(result);

        } catch (SQLException e) {
            return toJson(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ========== 辅助方法 ==========

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (++count < map.size()) json.append(",");
            json.append("\n");
        }
        json.append("}");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String str = (String) value;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return "\"" + str + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List) return formatList((List<?>) value);
        if (value instanceof Map) return formatMap((Map<?, ?>) value);
        return "\"" + value + "\"";
    }

    private String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append("    ").append(formatValue(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private String formatMap(Map<?, ?> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ");
            sb.append(formatValue(entry.getValue()));
            if (++i < map.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }
}