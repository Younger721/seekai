package com.njhtr.seekai.skill;

import java.util.Map;

/**
 * 技能执行器接口
 */
public interface SkillExecutor {

    /**
     * 执行技能
     *
     * @param skill    技能定义
     * @param params   执行参数
     * @param context  执行上下文
     * @return 执行结果
     */
    SkillResult execute(Skill skill, Map<String, Object> params, ExecutionContext context);

    /**
     * 验证参数
     *
     * @param skill  技能定义
     * @param params 参数
     * @return 是否有效
     */
    boolean validate(Skill skill, Map<String, Object> params);

    /**
     * 获取技能类型
     */
    String getType();

    /**
     * 执行结果
     */
    @lombok.Data
    class SkillResult {
        private boolean success;
        private String output;
        private String error;
        private long executionTimeMs;
        private Map<String, Object> metadata;

        public static SkillResult ok(String output) {
            SkillResult result = new SkillResult();
            result.setSuccess(true);
            result.setOutput(output);
            return result;
        }

        public static SkillResult fail(String error) {
            SkillResult result = new SkillResult();
            result.setSuccess(false);
            result.setError(error);
            return result;
        }
    }

    /**
     * 执行上下文
     */
    @lombok.Data
    class ExecutionContext {
        private String userId;
        private String conversationId;
        private String sessionId;
        private Map<String, Object> sharedData;
        private PermissionChecker permissionChecker;

        public ExecutionContext() {
            this.sharedData = new java.util.HashMap<>();
        }
    }

    /**
     * 权限检查器
     */
    interface PermissionChecker {
        boolean hasPermission(Skill.Permission required);
    }
}