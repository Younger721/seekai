package com.njhtr.seekai.controller;

import com.njhtr.seekai.security.Permission;
import com.njhtr.seekai.security.SecurityContext;
import com.njhtr.seekai.security.SecurityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 权限沙盒 API
 */
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecurityManager securityManager;

    /**
     * 获取当前安全级别
     */
    @GetMapping("/level")
    public Map<String, Object> getSecurityLevel(
            @RequestParam String userId,
            @RequestParam(required = false) String sessionId
    ) {
        String session = sessionId != null ? sessionId : "default";
        Permission.SecurityLevel level = securityManager.getSecurityLevel(userId, session);

        return Map.of(
            "userId", userId,
            "securityLevel", level.getName(),
            "description", level.getDescription(),
            "allowedPermissions", level.getAllowedPermissions()
        );
    }

    /**
     * 设置安全级别
     */
    @PostMapping("/level")
    public Map<String, Object> setSecurityLevel(
            @RequestParam String userId,
            @RequestParam String level,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            Permission.SecurityLevel newLevel = Permission.SecurityLevel.valueOf(level.toUpperCase());
            String session = sessionId != null ? sessionId : "default";
            securityManager.setSecurityLevel(userId, session, newLevel);

            return Map.of(
                "success", true,
                "message", "安全级别已设置为: " + newLevel.getName(),
                "level", newLevel.getName()
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "success", false,
                "error", "无效的安全级别，可选: sandbox, restricted, standard, trusted"
            );
        }
    }

    /**
     * 获取所有安全级别选项
     */
    @GetMapping("/levels")
    public Map<String, Object> getAvailableLevels() {
        List<Map<String, Object>> levels = List.of(
            Map.of("name", "SANDBOX", "display", "沙盒模式", "description", "仅允许安全的基本操作"),
            Map.of("name", "RESTRICTED", "display", "受限模式", "description", "允许部分写入和受限命令"),
            Map.of("name", "STANDARD", "display", "标准模式", "description", "日常开发所需权限"),
            Map.of("name", "TRUSTED", "display", "信任模式", "description", "完全信任，可执行所有操作")
        );

        return Map.of("levels", levels);
    }

    /**
     * 检查权限
     */
    @GetMapping("/check")
    public Map<String, Object> checkPermission(
            @RequestParam String userId,
            @RequestParam String permission,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            String session = sessionId != null ? sessionId : "default";
            Permission.PermissionType perm = Permission.PermissionType.valueOf(permission.toUpperCase());
            boolean allowed = securityManager.checkPermission(userId, session, perm);

            return Map.of(
                "userId", userId,
                "permission", permission,
                "allowed", allowed
            );
        } catch (Exception e) {
            return Map.of(
                "error", "无效的权限类型: " + permission
            );
        }
    }

    /**
     * 临时授权权限
     */
    @PostMapping("/grant")
    public Map<String, Object> grantPermission(
            @RequestParam String userId,
            @RequestParam String permission,
            @RequestParam(required = false) String sessionId
    ) {
        try {
            String session = sessionId != null ? sessionId : "default";
            Permission.PermissionType perm = Permission.PermissionType.valueOf(permission.toUpperCase());
            securityManager.grantTempPermission(userId, session, perm);

            return Map.of(
                "success", true,
                "message", "已临时授权: " + permission
            );
        } catch (Exception e) {
            return Map.of(
                "success", false,
                "error", "无效的权限类型: " + permission
            );
        }
    }

    /**
     * 添加白名单
     */
    @PostMapping("/whitelist")
    public Map<String, Object> addWhitelist(
            @RequestParam String userId,
            @RequestParam String pattern,
            @RequestParam(required = false) String sessionId
    ) {
        String session = sessionId != null ? sessionId : "default";
        securityManager.addToWhitelist(userId, session, pattern);

        return Map.of(
            "success", true,
            "message", "已添加白名单: " + pattern
        );
    }

    /**
     * 添加黑名单
     */
    @PostMapping("/blacklist")
    public Map<String, Object> addBlacklist(
            @RequestParam String userId,
            @RequestParam String pattern,
            @RequestParam(required = false) String sessionId
    ) {
        String session = sessionId != null ? sessionId : "default";
        securityManager.addToBlacklist(userId, session, pattern);

        return Map.of(
            "success", true,
            "message", "已添加黑名单: " + pattern
        );
    }

    /**
     * 获取用户统计
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestParam String userId,
            @RequestParam(required = false) String sessionId
    ) {
        String session = sessionId != null ? sessionId : "default";
        return securityManager.getUserStats(userId, session);
    }

    /**
     * 获取所有会话
     */
    @GetMapping("/sessions")
    public Map<String, Object> getAllSessions() {
        return Map.of(
            "sessions", securityManager.getAllContexts(),
            "total", securityManager.getAllContexts().size()
        );
    }

    /**
     * 清理会话
     */
    @DeleteMapping("/session")
    public Map<String, Object> clearSession(
            @RequestParam String userId,
            @RequestParam(required = false) String sessionId
    ) {
        String session = sessionId != null ? sessionId : "default";
        securityManager.clearSession(userId, session);

        return Map.of(
            "success", true,
            "message", "会话已清理"
        );
    }
}