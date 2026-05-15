package com.njhtr.seekai.skill;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 技能定义
 */
@Data
public class Skill {
    private String id;              // 唯一标识
    private String name;            // 技能名称
    private String description;     // 技能描述
    private String category;        // 分类: data, tool, automation, analysis 等
    private String version;         // 版本
    private List<String> tags;      // 标签
    private String source;          // 来源: builtin, plugin, custom
    private String trigger;         // 触发词

    // 执行配置
    private String entryPoint;      // 入口方法/类
    private Map<String, Object> config; // 配置参数

    // 权限要求
    private Permission permission;  // 所需权限

    // 元数据
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int usageCount;         // 使用次数

    // 能力定义
    private List<SkillCapability> capabilities;

    public Skill() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.permission = Permission.READ_ONLY;
    }

    /**
     * 技能能力定义
     */
    @Data
    public static class SkillCapability {
        private String name;        // 能力名称
        private String description; // 描述
        private List<Parameter> parameters; // 参数列表
        private String returns;     // 返回类型描述
    }

    @Data
    public static class Parameter {
        private String name;
        private String type;
        private boolean required;
        private String description;
        private String defaultValue;
    }

    /**
     * 权限级别
     */
    public enum Permission {
        READ_ONLY,      // 只读权限
        FILE_READ,      // 读取文件
        FILE_WRITE,     // 写入文件
        COMMAND,        // 执行命令
        NETWORK,        // 网络访问
        BROWSER,        // 浏览器控制
        DATABASE,       // 数据库操作
        FULL_ACCESS     // 完全权限
    }
}