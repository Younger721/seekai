package com.njhtr.seekai.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 安全工具包装器 - 为工具添加权限检查
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecureToolWrapper {

    private final SecurityManager securityManager;

    /**
     * 执行文件读取 (带权限检查)
     */
    public String readFile(String userId, String sessionId, String path) {
        // 检查权限
        if (!securityManager.checkPermission(userId, sessionId, Permission.PermissionType.FILE_READ)) {
            throw new SecurityManager.SecurityException("权限不足: FILE_READ");
        }

        // 检查资源
        if (!securityManager.checkResource(userId, sessionId, path)) {
            throw new SecurityManager.SecurityException("禁止访问资源: " + path);
        }

        return "文件内容: " + path; // 实际读取由 FileManagerTools 执行
    }

    /**
     * 执行文件写入 (带权限检查)
     */
    public String writeFile(String userId, String sessionId, String path, String content) {
        if (!securityManager.checkPermission(userId, sessionId, Permission.PermissionType.FILE_WRITE)) {
            throw new SecurityManager.SecurityException("权限不足: FILE_WRITE");
        }

        if (!securityManager.checkResource(userId, sessionId, path)) {
            throw new SecurityManager.SecurityException("禁止访问资源: " + path);
        }

        return "写入成功: " + path;
    }

    /**
     * 执行系统命令 (带权限检查)
     */
    public String executeCommand(String userId, String sessionId, String command) {
        if (!securityManager.checkPermission(userId, sessionId, Permission.PermissionType.COMMAND_EXECUTE)) {
            throw new SecurityManager.SecurityException("权限不足: COMMAND_EXECUTE");
        }

        return "执行命令: " + command;
    }

    /**
     * 执行网络请求 (带权限检查)
     */
    public String makeRequest(String userId, String sessionId, String url) {
        if (!securityManager.checkPermission(userId, sessionId, Permission.PermissionType.NETWORK_REQUEST)) {
            throw new SecurityManager.SecurityException("权限不足: NETWORK_REQUEST");
        }

        return "发送请求: " + url;
    }

    /**
     * 控制浏览器 (带权限检查)
     */
    public String controlBrowser(String userId, String sessionId, String action) {
        if (!securityManager.checkPermission(userId, sessionId, Permission.PermissionType.BROWSER_CONTROL)) {
            throw new SecurityManager.SecurityException("权限不足: BROWSER_CONTROL");
        }

        return "浏览器操作: " + action;
    }

    /**
     * 安全执行 - 带自动权限检查的通用方法
     */
    public <T> T executeSecure(String userId, String sessionId,
                                Permission.PermissionType permission,
                                Supplier<T> operation) {
        return securityManager.executeWithPermission(userId, sessionId, permission, operation);
    }
}