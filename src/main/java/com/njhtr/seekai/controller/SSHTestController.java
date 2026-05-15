package com.njhtr.seekai.controller;

import com.njhtr.seekai.dto.ApiResponse;
import com.njhtr.seekai.tool.SystemOperationTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SSH 测试控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class SSHTestController {

    private final SystemOperationTools systemOperationTools;

    /**
     * 测试 SSH 连接
     */
    @GetMapping("/ssh")
    public ResponseEntity<ApiResponse<String>> testSSH(
            @RequestParam String host,
            @RequestParam(defaultValue = "22") int port,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "echo 'SSH connection successful!'") String command) {

        log.info("🧪 测试 SSH 连接: {}@{}:{}", username, host, port);

        try {
            String result = systemOperationTools.executeSSHCommand(host, port, username, password, command);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.error("❌ SSH 测试失败: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.error("SSH 连接失败: " + e.getMessage()));
        }
    }

    /**
     * 测试 ping 连接
     */
    @GetMapping("/ping")
    public ResponseEntity<ApiResponse<String>> testPing(@RequestParam String host) {
        log.info("🧪 测试 Ping: {}", host);

        try {
            String result = systemOperationTools.pingHost(host);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("Ping 失败: " + e.getMessage()));
        }
    }

    /**
     * 测试本地命令执行
     */
    @GetMapping("/cmd")
    public ResponseEntity<ApiResponse<String>> testCommand(@RequestParam String command) {
        log.info("🧪 测试命令: {}", command);

        try {
            String result = systemOperationTools.executeLocalCommand(command);
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error("命令执行失败: " + e.getMessage()));
        }
    }
}