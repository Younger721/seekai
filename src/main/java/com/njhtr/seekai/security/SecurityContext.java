package com.njhtr.seekai.security;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 安全上下文 - 每个用户/会话的安全状态
 */
@Slf4j
@Data
public class SecurityContext {

    private String userId;
    private String sessionId;
    private Permission.SecurityLevel securityLevel;

    // 已授予的权限
    private Set<Permission.PermissionType> grantedPermissions = new HashSet<>();

    // 权限拒绝记录
    private List<PermissionDeniedRecord> deniedRecords = new ArrayList<>();

    // 操作日志
    private List<OperationLog> operationLogs = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime lastActive;

    // 白名单/黑名单
    private List<String> whitelist = new ArrayList<>();     // 允许的资源
    private List<String> blacklist = new ArrayList<>();     // 禁止的资源

    public SecurityContext(String userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.securityLevel = Permission.SecurityLevel.SANDBOX;
        this.createdAt = LocalDateTime.now();
        this.lastActive = LocalDateTime.now();

        // 从安全级别初始化权限
        this.grantedPermissions.addAll(securityLevel.getAllowedPermissions());
    }

    /**
     * 检查是否有权限
     */
    public boolean hasPermission(Permission.PermissionType permission) {
        updateLastActive();

        // 检查安全级别
        if (securityLevel.can(permission)) {
            return true;
        }

        // 检查额外授予的权限
        if (grantedPermissions.contains(permission)) {
            return true;
        }

        // 记录拒绝
        deniedRecords.add(new PermissionDeniedRecord(permission, "安全级别不允许"));
        log.warn("🚫 权限拒绝: {} (用户: {}, 级别: {})",
            permission, userId, securityLevel);

        return false;
    }

    /**
     * 检查资源是否允许
     */
    public boolean isResourceAllowed(String resource) {
        // 黑名单优先
        for (String pattern : blacklist) {
            if (matchesPattern(resource, pattern)) {
                return false;
            }
        }

        // 白名单检查
        if (whitelist.isEmpty()) {
            return true;
        }

        for (String pattern : whitelist) {
            if (matchesPattern(resource, pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 升级安全级别
     */
    public void upgradeLevel(Permission.SecurityLevel newLevel) {
        if (newLevel.getLevel() > securityLevel.getLevel()) {
            securityLevel = newLevel;
            grantedPermissions.addAll(newLevel.getAllowedPermissions());
            log.info("🔐 安全级别升级: {} -> {}", userId, newLevel.getName());
        }
    }

    /**
     * 降级安全级别
     */
    public void downgradeLevel(Permission.SecurityLevel newLevel) {
        securityLevel = newLevel;
        // 重新计算可用权限
        grantedPermissions.clear();
        grantedPermissions.addAll(newLevel.getAllowedPermissions());
        log.info("🔐 安全级别降级: {} -> {}", userId, newLevel.getName());
    }

    /**
     * 日志记录
     */
    public void logOperation(String operation, String resource, boolean success) {
        operationLogs.add(new OperationLog(operation, resource, success));
        updateLastActive();
    }

    /**
     * 获取统计
     */
    public Map<String, Object> getStats() {
        return Map.of(
            "userId", userId,
            "securityLevel", securityLevel.getName(),
            "grantedPermissions", grantedPermissions.size(),
            "totalDenied", deniedRecords.size(),
            "totalOperations", operationLogs.size(),
            "lastActive", lastActive.toString()
        );
    }

    private void updateLastActive() {
        this.lastActive = LocalDateTime.now();
    }

    private boolean matchesPattern(String resource, String pattern) {
        // 简单通配符匹配
        if (pattern.equals("*")) return true;

        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return resource.startsWith(prefix);
        }

        return resource.equals(pattern);
    }

    /**
     * 权限拒绝记录
     */
    @Data
    public static class PermissionDeniedRecord {
        private final Permission.PermissionType permission;
        private final String reason;
        private final LocalDateTime timestamp = LocalDateTime.now();
    }

    /**
     * 操作日志
     */
    @Data
    public static class OperationLog {
        private final String operation;
        private final String resource;
        private final boolean success;
        private final LocalDateTime timestamp = LocalDateTime.now();
    }
}