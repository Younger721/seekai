package com.njhtr.seekai.security;

import lombok.Data;

/**
 * 权限定义
 */
@Data
public class Permission {

    /**
     * 权限类型
     */
    public enum PermissionType {
        // 文件操作
        FILE_READ,           // 读取文件
        FILE_WRITE,          // 写入文件
        FILE_DELETE,         // 删除文件
        FILE_EXECUTE,        // 执行文件

        // 命令操作
        COMMAND_EXECUTE,     // 执行系统命令
        SHELL_SCRIPT,        // 执行Shell脚本

        // 网络操作
        NETWORK_REQUEST,     // 发送HTTP请求
        NETWORK_CONNECT,     // 建立网络连接

        // 浏览器操作
        BROWSER_CONTROL,     // 控制浏览器

        // 数据库操作
        DATABASE_READ,       // 读取数据库
        DATABASE_WRITE,      // 写入数据库

        // 系统操作
        SYSTEM_INFO,         // 获取系统信息
        ENVIRONMENT_VAR,     // 访问环境变量

        // 敏感操作
        DANGEROUS_FILE,      // 危险文件操作
        CROSS_BORDER,        // 跨域操作

        // 完全控制
        ALL                  // 全部权限
    }

    /**
     * 权限级别 (用户可选的安全等级)
     */
    public enum SecurityLevel {
        /**
         * 沙盒模式 - 最安全
         * 仅允许：读取文件、HTTP请求、系统信息
         */
        SANDBOX(1, "沙盒模式", "仅允许安全的基本操作"),

        /**
         * 受限模式
         * 允许：读取+部分写入、受限命令
         */
        RESTRICTED(2, "受限模式", "允许部分写入和受限命令"),

        /**
         * 标准模式
         * 允许：文件读写、命令执行、网络请求
         */
        STANDARD(3, "标准模式", "日常开发所需权限"),

        /**
         * 信任模式 - 完全控制
         * 允许：所有操作
         */
        TRUSTED(4, "信任模式", "完全信任，可执行所有操作");

        private final int level;
        private final String name;
        private final String description;

        SecurityLevel(int level, String name, String description) {
            this.level = level;
            this.name = name;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getName() { return name; }
        public String getDescription() { return description; }

        public boolean can(PermissionType permission) {
            return getAllowedPermissions().contains(permission);
        }

        public java.util.Set<PermissionType> getAllowedPermissions() {
            return switch (this) {
                case SANDBOX -> java.util.Set.of(
                    PermissionType.FILE_READ,
                    PermissionType.NETWORK_REQUEST,
                    PermissionType.SYSTEM_INFO
                );
                case RESTRICTED -> java.util.Set.of(
                    PermissionType.FILE_READ,
                    PermissionType.FILE_WRITE,
                    PermissionType.NETWORK_REQUEST,
                    PermissionType.SYSTEM_INFO,
                    PermissionType.ENVIRONMENT_VAR
                );
                case STANDARD -> java.util.Set.of(
                    PermissionType.FILE_READ,
                    PermissionType.FILE_WRITE,
                    PermissionType.FILE_DELETE,
                    PermissionType.COMMAND_EXECUTE,
                    PermissionType.SHELL_SCRIPT,
                    PermissionType.NETWORK_REQUEST,
                    PermissionType.NETWORK_CONNECT,
                    PermissionType.BROWSER_CONTROL,
                    PermissionType.DATABASE_READ,
                    PermissionType.DATABASE_WRITE,
                    PermissionType.SYSTEM_INFO,
                    PermissionType.ENVIRONMENT_VAR
                );
                case TRUSTED -> java.util.Set.of(PermissionType.values());
            };
        }
    }

    private PermissionType type;
    private String resource;      // 资源路径 (如 /root/*.txt)
    private String reason;        // 申请原因
    private long requestedAt;
    private boolean granted;

    public Permission(PermissionType type) {
        this.type = type;
        this.requestedAt = System.currentTimeMillis();
    }
}