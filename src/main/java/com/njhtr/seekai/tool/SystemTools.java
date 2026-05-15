package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Function;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 系统控制工具 - 提供安全的命令行执行能力
 */
@Slf4j
@Component
public class SystemTools {

    // 危险命令黑名单（简单的防御机制，防止破坏性操作）
    private static final List<String> BLACKLIST = Arrays.asList(
            "rm", "del", "format", "shutdown", "reboot",
            "diskpart", "mkfs", "fdisk", "drop", "truncate"
    );
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s`\"'<>]+|www\\.[^\\s`\"'<>]+)");

    /**
     * 1. 读文件功能
     */
    public Function<FileRequest, FileResponse> readFile() {
        return request -> {
            String filePath = request.path();
            log.info("📄 请求读取文件：[{}]", filePath);
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    return new FileResponse(filePath, false, "Error: 文件不存在", "");
                }
                if (!Files.isRegularFile(path)) {
                    return new FileResponse(filePath, false, "Error: 路径不是一个文件", "");
                }
                
                // 限制读取文件大小 (例如最大 1MB)，防止内存溢出或内容过长影响 Token
                long size = Files.size(path);
                if (size > 1024 * 1024) {
                    return new FileResponse(filePath, false, "Error: 文件过大，拒绝读取（限制为 1MB）", "");
                }
                
                String content = Files.readString(path);
                return new FileResponse(filePath, true, "文件读取成功", content);
            } catch (Exception e) {
                log.error("❌ 读取文件失败：{}", e.getMessage());
                return new FileResponse(filePath, false, "Exception: " + e.getMessage(), "");
            }
        };
    }

    /**
     * 2. 写文件功能
     */
    public Function<FileWriteRequest, FileResponse> writeFile() {
        return request -> {
            String filePath = request.path();
            String content = request.content();
            boolean append = request.append() != null && request.append();
            log.warn("💾 请求写入文件：[{}], 追加模式：{}", filePath, append);
            
            try {
                Path path = Paths.get(filePath);
                // 确保父目录存在
                if (path.getParent() != null && !Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                
                if (append) {
                    Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } else {
                    Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                }
                
                return new FileResponse(filePath, true, "文件写入成功", "");
            } catch (Exception e) {
                log.error("❌ 写入文件失败：{}", e.getMessage());
                return new FileResponse(filePath, false, "Exception: " + e.getMessage(), "");
            }
        };
    }

    /**
     * 3. 浏览器/网页/应用自动化调用
     */
    public Function<UrlRequest, CommandResponse> openUrl() {
        return request -> {
            String url = request.url();
            log.info("🌐 请求打开网址或应用：[{}]", url);
            try {
                // 优先尝试使用 Desktop API
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                    return new CommandResponse(url, true, "", "系统默认浏览器已成功打开该网址 (Desktop API)");
                } 
                
                // 降级方案：使用底层命令行强制打开浏览器
                log.warn("⚠️ Desktop API 不可用，尝试使用终端命令降级打开网址...");
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder processBuilder = new ProcessBuilder();
                
                if (os.contains("win")) {
                    processBuilder.command("cmd.exe", "/c", "start " + url);
                } else if (os.contains("mac")) {
                    processBuilder.command("open", url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    processBuilder.command("xdg-open", url);
                } else {
                    return new CommandResponse(url, false, "", "Error: 未知的操作系统，无法执行降级打开操作");
                }
                
                processBuilder.start();
                return new CommandResponse(url, true, "", "已通过系统命令尝试打开网址 (Fallback)");
                
            } catch (Exception e) {
                log.error("❌ 打开网址失败：{}", e.getMessage());
                return new CommandResponse(url, false, "", "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 4. 目录树扫描功能 (listFiles)
     */
    public Function<ListFilesRequest, CommandResponse> listFiles() {
        return request -> {
            String dirPath = request.path();
            log.info("📂 请求扫描目录：[{}]", dirPath);
            try {
                Path dir = Paths.get(dirPath);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    return new CommandResponse("listFiles " + dirPath, false, "", "Error: 目录不存在或不是文件夹");
                }
                
                // 限制扫描深度为 2 层，过滤掉 node_modules, .git 等，防止输出过大
                try (Stream<Path> walk = Files.walk(dir, 2)) {
                    List<String> result = walk
                            .filter(p -> !p.toString().contains("node_modules") 
                                      && !p.toString().contains(".git") 
                                      && !p.toString().contains(".idea")
                                      && !p.toString().contains("target"))
                            .map(p -> {
                                boolean isDir = Files.isDirectory(p);
                                return (isDir ? "[DIR] " : "[FILE] ") + dir.relativize(p).toString();
                            })
                            .collect(Collectors.toList());
                    
                    return new CommandResponse("listFiles " + dirPath, true, String.join("\n", result), "目录扫描成功");
                }
            } catch (Exception e) {
                log.error("❌ 目录扫描失败：{}", e.getMessage());
                return new CommandResponse("listFiles " + dirPath, false, "", "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 5. 全局搜索文件内容 (searchCode)
     */
    public Function<SearchCodeRequest, CommandResponse> searchCode() {
        return request -> {
            String dirPath = request.dir();
            String keyword = request.keyword();
            log.info("🔎 请求搜索代码：目录=[{}], 关键词=[{}]", dirPath, keyword);
            try {
                Path dir = Paths.get(dirPath);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    return new CommandResponse("searchCode " + keyword, false, "", "Error: 目录不存在或不是文件夹");
                }

                List<String> matches = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(dir, 5)) { // 搜索深度稍微深一点
                    walk.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("node_modules") 
                                  && !p.toString().contains(".git") 
                                  && !p.toString().contains(".idea")
                                  && !p.toString().contains("target")
                                  && !p.toString().endsWith(".jar")
                                  && !p.toString().endsWith(".class")
                                  && !p.toString().endsWith(".exe")
                                  && !p.toString().endsWith(".dll"))
                        .forEach(file -> {
                            try {
                                // 读取文件内容并查找关键字
                                List<String> lines = Files.readAllLines(file);
                                for (int i = 0; i < lines.size(); i++) {
                                    if (lines.get(i).contains(keyword)) {
                                        // 记录匹配的相对路径、行号和代码片段
                                        matches.add(String.format("%s:Line %d: %s", 
                                                dir.relativize(file).toString(), 
                                                (i + 1), 
                                                lines.get(i).trim()));
                                    }
                                }
                            } catch (Exception e) {
                                // 忽略无法读取的二进制文件或编码错误
                            }
                        });
                }
                
                if (matches.isEmpty()) {
                    return new CommandResponse("searchCode " + keyword, true, "未找到包含 '" + keyword + "' 的结果", "搜索完成");
                }
                
                // 防止搜索结果过多撑爆上下文，限制最多返回 100 条
                String output = matches.stream().limit(100).collect(Collectors.joining("\n"));
                if (matches.size() > 100) {
                    output += "\n... (结果过多，仅显示前 100 条)";
                }
                return new CommandResponse("searchCode " + keyword, true, output, "共找到 " + matches.size() + " 条结果");

            } catch (Exception e) {
                log.error("❌ 搜索代码失败：{}", e.getMessage());
                return new CommandResponse("searchCode " + keyword, false, "", "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 6. 精准代码替换打补丁 (patchFile)
     */
    public Function<PatchFileRequest, FileResponse> patchFile() {
        return request -> {
            String filePath = request.path();
            String searchStr = request.searchStr();
            String replaceStr = request.replaceStr();
            log.warn("✂️ 请求修改文件片段：[{}]", filePath);
            
            try {
                Path path = Paths.get(filePath);
                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    return new FileResponse(filePath, false, "Error: 文件不存在", "");
                }
                
                String content = Files.readString(path);
                
                if (!content.contains(searchStr)) {
                    return new FileResponse(filePath, false, "Error: 未在文件中找到完全匹配的搜索字符串(searchStr)，请确保缩进和换行符完全一致！", "");
                }
                
                // 仅替换第一次出现的匹配项
                String newContent = content.replaceFirst(
                    java.util.regex.Pattern.quote(searchStr), 
                    java.util.regex.Matcher.quoteReplacement(replaceStr)
                );
                
                Files.writeString(path, newContent, StandardOpenOption.TRUNCATE_EXISTING);
                
                return new FileResponse(filePath, true, "文件片段替换成功", "");
            } catch (Exception e) {
                log.error("❌ 替换文件内容失败：{}", e.getMessage());
                return new FileResponse(filePath, false, "Exception: " + e.getMessage(), "");
            }
        };
    }

    /**
     * 7. 运行自动化测试 (runTests)
     */
    public Function<RunTestRequest, CommandResponse> runTests() {
        return request -> {
            String dirPath = request.dir();
            String testCommand = request.testCommand();
            log.info("🧪 请求执行测试命令：目录=[{}], 命令=[{}]", dirPath, testCommand);
            try {
                Path dir = Paths.get(dirPath);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    return new CommandResponse("runTests " + testCommand, false, "", "Error: 测试目录不存在");
                }

                // 判断操作系统
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.directory(dir.toFile());

                if (isWindows) {
                    processBuilder.command("cmd.exe", "/c", testCommand);
                } else {
                    processBuilder.command("sh", "-c", testCommand);
                }

                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                // 读取测试输出
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        lineCount++;
                        // 限制输出长度，防止 Token 爆炸
                        if (lineCount > 500) {
                            output.append("... (测试输出过长，已截断)\n");
                            break;
                        }
                    }
                }

                int exitCode = process.waitFor();
                boolean success = (exitCode == 0);
                String msg = success ? "✅ 测试全部通过！" : "❌ 测试失败，存在未通过的用例或编译错误，请查看日志修复代码。退出码：" + exitCode;

                log.info("🧪 测试执行完成，结果：{}", success);
                return new CommandResponse("runTests " + testCommand, success, output.toString().trim(), msg);

            } catch (Exception e) {
                log.error("❌ 执行测试失败：{}", e.getMessage());
                return new CommandResponse("runTests " + testCommand, false, "", "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 8. 抓取网页正文内容 (scrapeWebPage)
     * 让大模型可以直接阅读某个 URL 的文字内容
     */
    public Function<UrlRequest, FileResponse> scrapeWebPage() {
        return request -> {
            String rawUrl = request.url();
            String url = normalizeUrl(rawUrl);
            log.info("🌐 正在抓取网页内容：{} -> {}", rawUrl, url);
            try {
                if (url == null || url.isEmpty() || !url.startsWith("http")) {
                    return new FileResponse(rawUrl, false, "Error: 无效的 URL，必须以 http 或 https 开头", "");
                }
                
                String text = "";
                boolean jsoupSuccess = false;
                
                try {
                    Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .referrer("https://www.google.com/")
                        .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                        .header("Sec-Ch-Ua-Mobile", "?0")
                        .header("Sec-Ch-Ua-Platform", "\"macOS\"")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Sec-Fetch-User", "?1")
                        .header("Upgrade-Insecure-Requests", "1")
                        .timeout(10000)
                        .followRedirects(true)
                        .get();
                    
                    // 移除无用的标签
                    doc.select("script, style, nav, footer, header, aside, iframe, svg, img").remove();
                    
                    text = doc.body().text();
                    if (!text.trim().isEmpty()) {
                        jsoupSuccess = true;
                        log.info("✅ Jsoup 抓取成功 (纯文本提取)");
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Jsoup 抓取抛出异常 [{}]: {}", url, e.getMessage());
                }
                
                // 如果提取出来的内容为空(SPA单页应用)或者抛出异常(强反爬)，触发 Jina Reader API 回退
                if (!jsoupSuccess) {
                    log.info("↪ 传统抓取失败，触发 Jina Reader API (专为大模型设计的渲染引擎) 回退抓取...");
                    try {
                        HttpClient client = HttpClient.newBuilder()
                                .followRedirects(HttpClient.Redirect.NORMAL)
                                .connectTimeout(Duration.ofSeconds(10))
                                .build();
                        
                        HttpRequest jinaReq = HttpRequest.newBuilder()
                                .uri(URI.create("https://r.jina.ai/" + url))
                                .header("Accept", "text/plain") // 要求返回 Markdown
                                .header("X-Return-Format", "markdown")
                                .timeout(Duration.ofSeconds(15))
                                .GET()
                                .build();
                        
                        HttpResponse<String> jinaResp = client.send(jinaReq, HttpResponse.BodyHandlers.ofString());
                        if (jinaResp.statusCode() == 200 && jinaResp.body() != null && !jinaResp.body().trim().isEmpty()) {
                            text = jinaResp.body();
                            log.info("✅ Jina Reader 回退抓取成功！");
                        } else {
                            return new FileResponse(url, false, "Error: 双重抓取失败。该网页可能无法访问或存在极强的验证码拦截。", "");
                        }
                    } catch (Exception ex) {
                        log.error("❌ Jina Reader 回退抓取也失败了: {}", ex.getMessage());
                        return new FileResponse(url, false, "Exception: 网页无法访问或读取超时", "");
                    }
                }
                
                // 限制返回长度，防止把大模型的上下文撑爆
                if (text.length() > 3000) {
                    text = text.substring(0, 3000) + "\n...[网页内容过长，已截断]";
                }
                
                return new FileResponse(url, true, "网页抓取成功", text);
            } catch (Exception e) {
                log.warn("❌ 网页抓取系统级失败 [{} -> {}]: {}", rawUrl, url, e.getMessage());
                return new FileResponse(url, false, "Exception: " + e.getMessage(), "");
            }
        };
    }

    public String normalizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }

        String normalized = rawUrl.trim();
        Matcher matcher = URL_PATTERN.matcher(normalized);
        if (matcher.find()) {
            normalized = matcher.group(1);
        }

        normalized = normalized.replaceAll("^[`'\"\\s<\\[(]+|[`'\"\\s>\\])]+$", "");

        if (normalized.startsWith("www.")) {
            normalized = "https://" + normalized;
        }

        return normalized.trim();
    }

    /**
     * 执行安全的系统命令函数
     */
    public Function<CommandRequest, CommandResponse> executeCommand() {
        return request -> {
            String command = request.command();
            log.warn("⚠️ 收到系统命令执行请求：[{}]", command);

            // 1. 安全检查：白名单/黑名单校验
            if (isDangerousCommand(command)) {
                log.error("❌ 拦截到高危命令：[{}]", command);
                return new CommandResponse(command, false, "系统拒绝执行：检测到高危或破坏性命令。", "Error: 权限不足或命令被拦截。");
            }

            // 2. 执行命令
            try {
                // 判断操作系统，Windows 用 cmd /c，Linux/Mac 用 sh -c
                boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
                ProcessBuilder processBuilder = new ProcessBuilder();

                if (isWindows) {
                    processBuilder.command("cmd.exe", "/c", command);
                } else {
                    processBuilder.command("sh", "-c", command);
                }

                // 合并标准输出和错误输出
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();

                // 收集输出
                StringBuilder output = new StringBuilder();
                // 解决 Windows 下中文乱码问题 (GBK)
                String charset = isWindows ? "GBK" : "UTF-8";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), charset))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                // 设置超时，防止命令卡死 (例如 ping -t)
                boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.error("⏱️ 命令执行超时被强杀：[{}]", command);
                    return new CommandResponse(command, false, output.toString(), "Error: 命令执行超时 (超过 10 秒)。");
                }

                int exitCode = process.exitValue();
                boolean success = exitCode == 0;
                log.info("✅ 命令执行完成，退出码：{}", exitCode);

                return new CommandResponse(
                        command,
                        success,
                        output.toString().trim(),
                        success ? "命令执行成功" : "命令执行失败，退出码: " + exitCode
                );

            } catch (Exception e) {
                log.error("❌ 命令执行发生异常：{}", e.getMessage(), e);
                return new CommandResponse(command, false, "", "Exception: " + e.getMessage());
            }
        };
    }

    /**
     * 检查是否包含危险命令
     */
    private boolean isDangerousCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return true;
        }
        String lowerCmd = command.toLowerCase();
        // 简单正则：检查单词边界
        for (String blackItem : BLACKLIST) {
            if (lowerCmd.matches(".*\\b" + blackItem + "\\b.*")) {
                return true;
            }
        }
        // 阻止重定向和管道等复杂操作 (更严格的安全限制)
        if (lowerCmd.contains(">") || lowerCmd.contains("|") || lowerCmd.contains("&")) {
            return true;
        }
        return false;
    }

    /**
     * 命令请求记录
     */
    public record CommandRequest(
            String command
    ) {}

    /**
     * 命令响应记录
     */
    public record CommandResponse(
            String command,
            boolean success,
            String output,
            String message
    ) {}

    public record FileRequest(String path) {}

    public record FileWriteRequest(String path, String content, Boolean append) {}

    public record FileResponse(String path, boolean success, String message, String content) {}

    public record UrlRequest(String url) {}

    public record ListFilesRequest(String path) {}

    public record SearchCodeRequest(String dir, String keyword) {}

    public record PatchFileRequest(String path, String searchStr, String replaceStr) {}

    public record RunTestRequest(String dir, String testCommand) {}
}
