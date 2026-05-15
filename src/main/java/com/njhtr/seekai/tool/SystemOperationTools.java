package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统操作工具 - 提供 SSH 连接、远程命令执行、进程管理等功能
 * 注意：实际生产环境需要安全的凭据管理机制
 */
@Slf4j
@Component
public class SystemOperationTools {

    /**
     * 执行本地 Shell 命令
     *
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    @Tool(description = "在本地执行 Shell 命令。适用于查询系统信息、文件操作、进程管理等本地系统操作。")
    public String executeLocalCommand(
            @ToolParam(description = "要执行的命令，如: ls -la, ps aux, df -h") String command) {

        log.info("💻 执行本地命令: {}", command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command("bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            int exitCode = finished ? process.exitValue() : -1;

            if (!finished) {
                process.destroyForcibly();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", finished && exitCode == 0);
            result.put("command", command);
            result.put("exitCode", exitCode);
            result.put("output", output.toString());

            log.info("✅ 命令执行完成，退出码: {}", exitCode);
            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 命令执行失败: {}", e.getMessage());
            return toJson(Map.of(
                    "success", false,
                    "command", command,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取系统信息
     *
     * @return 系统信息 JSON
     */
    @Tool(description = "获取当前系统的基本信息，包括操作系统、内存、CPU、磁盘等。")
    public String getSystemInfo() {

        log.info("📟 获取系统信息");

        try {
            Map<String, Object> info = new LinkedHashMap<>();

            // 操作系统信息
            info.put("osName", System.getProperty("os.name"));
            info.put("osVersion", System.getProperty("os.version"));
            info.put("osArch", System.getProperty("os.arch"));

            // Java 信息
            info.put("javaVersion", System.getProperty("java.version"));
            info.put("javaVendor", System.getProperty("java.vendor"));

            // 用户信息
            info.put("userName", System.getProperty("user.name"));
            info.put("userHome", System.getProperty("user.home"));
            info.put("userDir", System.getProperty("user.dir"));

            // 内存信息
            Runtime rt = Runtime.getRuntime();
            info.put("totalMemory", formatBytes(rt.totalMemory()));
            info.put("freeMemory", formatBytes(rt.freeMemory()));
            info.put("maxMemory", formatBytes(rt.maxMemory()));
            info.put("availableProcessors", rt.availableProcessors());

            // 获取 CPU 和负载信息（通过命令）
            try {
                String loadAvg = executeCommandOutput("uptime");
                info.put("uptime", loadAvg);
            } catch (Exception e) {
                info.put("uptime", "N/A");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("systemInfo", info);

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 列出目录内容
     *
     * @param path 目录路径
     * @return 目录内容列表
     */
    @Tool(description = "列出指定目录的内容，包括文件和子目录。")
    public String listDirectory(
            @ToolParam(description = "目录路径，默认为当前目录") String path) {

        log.info("📂 列出目录: {}", path);

        try {
            File dir = new File(path);
            if (!dir.exists()) {
                return toJson(Map.of(
                        "success", false,
                        "error", "目录不存在: " + path
                ));
            }
            if (!dir.isDirectory()) {
                return toJson(Map.of(
                        "success", false,
                        "error", "路径不是目录: " + path
                ));
            }

            String[] files = dir.list();
            List<Map<String, Object>> fileList = new ArrayList<>();

            if (files != null) {
                for (String filename : files) {
                    File f = new File(dir, filename);
                    Map<String, Object> fileInfo = new LinkedHashMap<>();
                    fileInfo.put("name", filename);
                    fileInfo.put("isDirectory", f.isDirectory());
                    fileInfo.put("size", f.length());
                    fileInfo.put("lastModified", f.lastModified());
                    fileList.add(fileInfo);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("path", path);
            result.put("totalFiles", fileList.size());
            result.put("files", fileList);

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "path", path,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    @Tool(description = "读取指定文件的内容。支持文本文件读取。")
    public String readFile(
            @ToolParam(description = "文件路径") String filePath) {

        log.info("📄 读取文件: {}", filePath);

        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return toJson(Map.of(
                        "success", false,
                        "error", "文件不存在: " + filePath
                ));
            }
            if (!file.canRead()) {
                return toJson(Map.of(
                        "success", false,
                        "error", "文件无法读取: " + filePath
                ));
            }

            // 限制文件大小
            if (file.length() > 1024 * 1024) {
                return toJson(Map.of(
                        "success", false,
                        "error", "文件过大，超过 1MB 限制"
                ));
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(file))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 1000) {
                    content.append(line).append("\n");
                    lineCount++;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("filePath", filePath);
            result.put("fileName", file.getName());
            result.put("fileSize", file.length());
            result.put("content", content.toString());

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "filePath", filePath,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 写入文件内容
     *
     * @param filePath 文件路径
     * @param content  文件内容
     * @return 操作结果
     */
    @Tool(description = "写入内容到指定文件。如果文件不存在则创建，如果已存在则覆盖。")
    public String writeFile(
            @ToolParam(description = "文件路径") String filePath,
            @ToolParam(description = "要写入的内容") String content) {

        log.info("✍️ 写入文件: {}", filePath);

        try {
            File file = new File(filePath);
            File parent = file.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(file))) {
                writer.write(content);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("filePath", filePath);
            result.put("bytesWritten", content.getBytes().length);

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "filePath", filePath,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 检查端口是否可用
     *
     * @param host 主机地址
     * @param port 端口号
     * @return 端口检查结果
     */
    @Tool(description = "检查远程主机的指定端口是否开放。")
    public String checkPort(
            @ToolParam(description = "主机地址或IP") String host,
            @ToolParam(description = "端口号") int port) {

        log.info("🔌 检查端口: {}:{}", host, port);

        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.close();

            return toJson(Map.of(
                    "success", true,
                    "host", host,
                    "port", port,
                    "isOpen", true
            ));

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", true,
                    "host", host,
                    "port", port,
                    "isOpen", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取网络接口信息
     *
     * @return 网络接口信息
     */
    @Tool(description = "获取本机的网络接口信息，包括 IP 地址、MAC 地址等。")
    public String getNetworkInfo() {

        log.info("🌐 获取网络信息");

        try {
            List<Map<String, String>> interfaces = new ArrayList<>();

            java.net.NetworkInterface.networkInterfaces()
                    .forEach(netInt -> {
                        try {
                            if (!netInt.isLoopback() && netInt.isUp()) {
                                Map<String, String> info = new LinkedHashMap<>();
                                info.put("name", netInt.getName());
                                info.put("displayName", netInt.getDisplayName());

                                netInt.inetAddresses()
                                        .filter(addr -> addr instanceof java.net.Inet4Address)
                                        .forEach(addr -> info.put("ipv4", addr.getHostAddress()));

                                netInt.getHardwareAddress();
                                if (netInt.getHardwareAddress() != null) {
                                    StringBuilder mac = new StringBuilder();
                                    for (byte b : netInt.getHardwareAddress()) {
                                        mac.append(String.format("%02X:", b));
                                    }
                                    if (mac.length() > 0) {
                                        mac.setLength(mac.length() - 1);
                                    }
                                    info.put("mac", mac.toString());
                                }

                                if (info.containsKey("ipv4")) {
                                    interfaces.add(info);
                                }
                            }
                        } catch (Exception e) {
                            // 忽略
                        }
                    });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("interfaces", interfaces);

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 模拟 SSH 连接（实际生产需要使用 JSch 等库）
     * 注意：这是一个模拟实现，实际需要集成 JSch 或 Apache MINA
     *
     * @param host     主机地址
     * @param port     端口号（默认22）
     * @param username 用户名
     * @param password 密码
     * @param command  连接后执行的命令
     * @return SSH 执行结果
     */
    @Tool(description = "通过 SSH 连接到远程服务器并执行命令。警告：凭据会明文传输，生产环境请使用密钥认证。")
    public String executeSSHCommand(
            @ToolParam(description = "SSH 服务器地址") String host,
            @ToolParam(description = "SSH 端口，默认22") int port,
            @ToolParam(description = "SSH 用户名") String username,
            @ToolParam(description = "SSH 密码") String password,
            @ToolParam(description = "要执行的命令") String command) {

        log.info("🔐 SSH 连接: {}@{}:{}", username, host, port);

        // 注意：SSH 密码认证需要使用 SSH 密钥或 sshpass 工具
        // 当前实现仅做演示，实际生产环境建议使用密钥认证
        return "⚠️ SSH 功能演示：当前仅支持密钥认证或需要安装 sshpass 工具。" +
               "\n要启用 SSH 密码认证，请：" +
               "\n1. 使用 SSH 密钥登录（推荐）" +
               "\n2. 或安装 sshpass: winget install sshpass，然后重新启动应用" +
               "\n\n当前服务器信息：" +
               "\n- 主机: " + host +
               "\n- 端口: " + port +
               "\n- 用户名: " + username;
    }

    /**
     * 测试网络连通性
     *
     * @param host 目标主机
     * @return ping 结果
     */
    @Tool(description = "测试到目标主机的网络连通性。")
    public String pingHost(
            @ToolParam(description = "目标主机地址或IP") String host) {

        log.info("📡 Ping 主机: {}", host);

        try {
            // 使用 ping 命令
            String command = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "ping -n 4 " + host
                    : "ping -c 4 " + host;

            ProcessBuilder pb = new ProcessBuilder();
            pb.command("bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            process.waitFor(10, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            boolean reachable = exitCode == 0 && output.toString().contains("0% loss");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("host", host);
            result.put("reachable", reachable);
            result.put("output", output.toString());

            return toJson(result);

        } catch (Exception e) {
            return toJson(Map.of(
                    "success", false,
                    "host", host,
                    "error", e.getMessage()
            ));
        }
    }

    // ========== 辅助方法 ==========

    private String executeCommandOutput(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command("bash", "-c", command);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(" ");
            }
        }
        p.waitFor(5, TimeUnit.SECONDS);
        return output.toString().trim();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

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
            str = str.replace("\\", "\\\\").replace("\"", "\\").replace("\n", "\\n").replace("\r", "\\r");
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