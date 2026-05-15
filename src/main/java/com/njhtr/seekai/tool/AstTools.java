package com.njhtr.seekai.tool;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;

/**
 * AST (抽象语法树) 工具类
 * 用于对代码进行精准的结构化修改，替代脆弱的字符串正则替换。
 * 目前支持：Java
 */
@Slf4j
@Component
public class AstTools {

    /**
     * 精准替换 Java 文件中的某个函数
     * 大模型只需提供文件名、函数名以及新的函数完整代码即可。
     */
    public Function<ReplaceFunctionRequest, AstResponse> replaceJavaFunction() {
        return request -> {
            String filePath = request.filePath();
            String functionName = request.functionName();
            String newFunctionCode = request.newFunctionCode();
            
            log.info("🌳 [AST] 请求替换 Java 函数：文件=[{}], 函数名=[{}]", filePath, functionName);
            
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return new AstResponse(false, "Error: 文件不存在或不是有效文件路径");
                }
                
                if (!filePath.endsWith(".java")) {
                    return new AstResponse(false, "Error: 该工具目前仅支持修改 .java 文件");
                }

                // 1. 解析原始的 Java 文件为 AST 树
                CompilationUnit cu = StaticJavaParser.parse(path);
                
                // 2. 查找对应的函数节点
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class, 
                        m -> m.getNameAsString().equals(functionName));
                
                if (methods.isEmpty()) {
                    return new AstResponse(false, "Error: 在文件中未找到名为 '" + functionName + "' 的函数");
                }
                if (methods.size() > 1) {
                    return new AstResponse(false, "Error: 找到多个同名函数（存在重载），目前仅支持唯一函数名替换。请手动指明或使用其它工具。");
                }
                
                MethodDeclaration oldMethod = methods.get(0);
                
                // 3. 将大模型提供的新代码片段解析为 AST 节点
                // 注意：大模型传过来的应该是一个完整的函数，比如 "public void foo() { ... }"
                MethodDeclaration newMethod;
                try {
                    newMethod = StaticJavaParser.parseMethodDeclaration(newFunctionCode);
                } catch (Exception e) {
                    log.error("解析新函数代码失败", e);
                    return new AstResponse(false, "Error: 您提供的新函数代码存在语法错误，无法解析为合法的 Java 方法。详情：" + e.getMessage());
                }
                
                // 4. 执行“换头手术”（将新节点替换掉老节点）
                oldMethod.replace(newMethod);
                
                // 5. 将修改后的 AST 重新生成代码并写回文件
                String modifiedCode = cu.toString();
                Files.writeString(path, modifiedCode);
                
                log.info("✅ [AST] 函数替换成功！");
                return new AstResponse(true, "函数 '" + functionName + "' 替换成功！代码已重新格式化并保存。");
                
            } catch (Exception e) {
                log.error("❌ AST 替换失败：{}", e.getMessage(), e);
                return new AstResponse(false, "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 语法检查工具 (checkSyntax)
     * 用于让大模型在写入代码后，自我验证代码是否能编译通过，实现沙盒闭环。
     */
    public Function<CheckSyntaxRequest, AstResponse> checkSyntax() {
        return request -> {
            String filePath = request.filePath();
            log.info("🔍 [AST] 请求语法检查：文件=[{}]", filePath);
            
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return new AstResponse(false, "Error: 文件不存在或不是有效文件路径");
                }

                if (filePath.endsWith(".java")) {
                    // Java 语法检查：尝试解析 AST
                    try {
                        StaticJavaParser.parse(path);
                        return new AstResponse(true, "✅ Java 语法检查通过：未发现明显的编译级语法错误。");
                    } catch (Exception e) {
                        return new AstResponse(false, "❌ Java 语法检查失败！存在语法错误，请根据报错信息修复代码：\n" + e.getMessage());
                    }
                } else if (filePath.endsWith(".json")) {
                    // 简单的 JSON 语法检查
                    try {
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(path));
                        return new AstResponse(true, "✅ JSON 语法检查通过。");
                    } catch (Exception e) {
                        return new AstResponse(false, "❌ JSON 语法错误：\n" + e.getMessage());
                    }
                } else {
                    return new AstResponse(true, "⚠️ 该工具目前仅支持对 .java 和 .json 文件进行强语法检查，其他文件默认返回通过。");
                }
            } catch (Exception e) {
                log.error("❌ 语法检查执行异常：{}", e.getMessage(), e);
                return new AstResponse(false, "Exception: 语法检查工具内部异常 - " + e.getMessage());
            }
        };
    }

    public record ReplaceFunctionRequest(String filePath, String functionName, String newFunctionCode) {}
    public record CheckSyntaxRequest(String filePath) {}
    public record AstResponse(boolean success, String message) {}
}
