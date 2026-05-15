package com.njhtr.seekai.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全管理器 - 统一管理所有安全操作
 */
@Slf4j
@Component
public class SecurityManager {

    // 用户安全上下文存储
    private final Map<String, SecurityContext> contexts = new ConcurrentHashMap<>();

    // 全局白名单/黑名单
    private final List<String> globalWhitelist = List.of(
        "/tmp/*",
        "/var/log/*",
        "/home/*/public/*"
    );

    private final List<String> globalBlacklist = List.of(
        "/etc/passwd",
        "/etc/shadow",
        "/.ssh/*",
        "C:\\Windows\\System32\\config\\*"
    );

    /**
     * 获取或创建安全上下文
     */
    public SecurityContext getContext(String userId, String sessionId) {
        String key = userId + ":" + sessionId;
        return contexts.computeIfAbsent(key, k -> {
            log.info("🔐 创建安全上下文: {}", userId);
            return new SecurityContext(userId, sessionId);
        });
    }

    /**
     * 检查权限
     */
    public boolean checkPermission(String userId, String sessionId,
                                    Permission.PermissionType permission) {
        SecurityContext ctx = getContext(userId, sessionId);

        // 先检查全局黑名单
        if (isGloballyBlocked(permission)) {
            log.warn("🚫 全局禁止的权限: {}", permission);
            return false;
        }

        return ctx.hasPermission(permission);
    }

    /**
     * 检查资源是否允许访问
     */
    public boolean checkResource(String userId, String sessionId, String resource) {
        SecurityContext ctx = getContext(userId, sessionId);

        // 检查全局黑名单
        if (isGlobalBlacklisted(resource)) {
            log.warn("🚫 全局禁止的资源: {}", resource);
            return false;
        }

        return ctx.isResourceAllowed(resource);
    }

    /**
     * 执行需要权限的操作 (带检查)
     */
    public <T> T executeWithPermission(String userId, String sessionId,
                                        Permission.PermissionType permission,
                                        java.util.function.Supplier<T> operation) {
        if (checkPermission(userId, sessionId, permission)) {
            return operation.get();
        } else {
            log.warn("🚫 权限不足: user={}, permission={}", userId, permission);
            throw new SecurityException("权限不足: " + permission);
        }
    }

    /**
     * 设置安全级别
     */
    public void setSecurityLevel(String userId, String sessionId,
                                  Permission.SecurityLevel level) {
        SecurityContext ctx = getContext(userId, sessionId);
        ctx.setSecurityLevel(level);
        log.info("🔐 设置安全级别: {} -> {}", userId, level.getName());
    }

    /**
     * 获取当前安全级别
     */
    public Permission.SecurityLevel getSecurityLevel(String userId, String sessionId) {
        return getContext(userId, sessionId).getSecurityLevel();
    }

    /**
     * 临时升级权限 (单次操作)
     */
    public void grantTempPermission(String userId, String sessionId,
                                     Permission.PermissionType permission) {
        SecurityContext ctx = getContext(userId, sessionId);
        ctx.getGrantedPermissions().add(permission);
        log.info("🔓 临时授权: {} -> {}", userId, permission);
    }

    /**
     * 添加资源到白名单
     */
    public void addToWhitelist(String userId, String sessionId, String pattern) {
        SecurityContext ctx = getContext(userId, sessionId);
        ctx.getWhitelist().add(pattern);
        log.info("📝 添加白名单: {} -> {}", userId, pattern);
    }

    /**
     * 添加资源到黑名单
     */
    public void addToBlacklist(String userId, String sessionId, String pattern) {
        SecurityContext ctx = getContext(userId, sessionId);
        ctx.getBlacklist().add(pattern);
        log.info("🚫 添加黑名单: {} -> {}", userId, pattern);
    }

    /**
     * 获取用户统计
     */
    public Map<String, Object> getUserStats(String userId, String sessionId) {
        return getContext(userId, sessionId).getStats();
    }

    /**
     * 获取所有上下文摘要
     */
    public List<Map<String, Object>> getAllContexts() {
        return contexts.values().stream()
            .map(SecurityContext::getStats)
            .toList();
    }

    /**
     * 清理会话
     */
    public void clearSession(String userId, String sessionId) {
        String key = userId + ":" + sessionId;
        contexts.remove(key);
        log.info("🗑️ 清理安全上下文: {}", key);
    }

    // ==================== 静态检查方法 ====================

    /**
     * 全局禁止的权限
     */
    private boolean isGloballyBlocked(Permission.PermissionType permission) {
        // 某些权限始终需要明确授权
        return permission == Permission.PermissionType.ALL;
    }

    /**
     * 全局黑名单资源
     */
    private boolean isGlobalBlacklisted(String resource) {
        for (String pattern : globalBlacklist) {
            if (matchesPattern(resource, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String resource, String pattern) {
        if (pattern.equals("*")) return true;
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return resource.startsWith(prefix);
        }
        return resource.equals(pattern);
    }

    /**
     * 安全异常
     */
    public static class SecurityException extends RuntimeException {
        public SecurityException(String message) {
            super(message);
        }
    }
}