package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * 文件管理工具 - 提供文件读写、目录操作、压缩解压等功能
 */
@Slf4j
@Component
public class FileManagerTools {

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容
     */
    @Tool(description = "读取指定文本文件的内容。支持 UTF-8 编码的文本文件。")
    public String readFile(
            @ToolParam(description = "要读取的文件完整路径") String filePath) {

        log.info("📄 读取文件: {}", filePath);

        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return jsonError("文件不存在: " + filePath);
            }
            if (!Files.isRegularFile(path)) {
                return jsonError("不是有效文件: " + filePath);
            }

            long fileSize = Files.size(path);
            if (fileSize > 5 * 1024 * 1024) {
                return jsonError("文件过大，超过 5MB 限制");
            }

            String content = Files.readString(path);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("filePath", filePath);
            result.put("fileName", path.getFileName().toString());
            result.put("fileSize", fileSize);
            result.put("content", content);

            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 读取文件失败: {}", e.getMessage());
            return jsonError(e.getMessage());
        }
    }

    /**
     * 写入文件内容
     *
     * @param filePath 文件路径
     * @param content  文件内容
     * @return 操作结果
     */
    @Tool(description = "写入内容到指定文件。如果文件不存在则创建，如果已存在则覆盖。")
    public String writeFile(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要写入的内容") String content) {

        log.info("✍️ 写入文件: {}", filePath);

        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();

            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("filePath", filePath);
            result.put("bytesWritten", content.getBytes().length);

            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 写入文件失败: {}", e.getMessage());
            return jsonError(e.getMessage());
        }
    }

    /**
     * 追加内容到文件
     *
     * @param filePath 文件路径
     * @param content  追加的内容
     * @return 操作结果
     */
    @Tool(description = "追加内容到文件末尾。如果文件不存在则创建。")
    public String appendToFile(
            @ToolParam(description = "文件完整路径") String filePath,
            @ToolParam(description = "要追加的内容") String content) {

        log.info("📝 追加到文件: {}", filePath);

        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();

            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Files.writeString(path, content, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("filePath", filePath);
            result.put("bytesAppended", content.getBytes().length);

            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 追加文件失败: {}", e.getMessage());
            return jsonError(e.getMessage());
        }
    }

    /**
     * 列出目录内容
     *
     * @param dirPath  目录路径
     * @param recursive 是否递归列出子目录
     * @return 目录内容列表
     */
    @Tool(description = "列出指定目录的内容。可以选择是否递归列出子目录。")
    public String listDirectory(
            @ToolParam(description = "目录路径") String dirPath,
            @ToolParam(description = "是否递归列出子目录，默认false") boolean recursive) {

        log.info("📂 列出目录: {} (recursive={})", dirPath, recursive);

        try {
            Path path = Paths.get(dirPath);
            if (!Files.exists(path)) {
                return jsonError("目录不存在: " + dirPath);
            }
            if (!Files.isDirectory(path)) {
                return jsonError("不是目录: " + dirPath);
            }

            List<Map<String, Object>> items = new ArrayList<>();

            if (recursive) {
                Files.walk(path, Integer.MAX_VALUE)
                        .filter(p -> !p.equals(path))
                        .limit(500)
                        .forEach(p -> items.add(createFileInfo(p, path)));
            } else {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.limit(500).forEach(p -> items.add(createFileInfo(p, path)));
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("dirPath", dirPath);
            result.put("totalItems", items.size());
            result.put("items", items);

            return toJson(result);

        } catch (Exception e) {
            log.error("❌ 列出目录失败: {}", e.getMessage());
            return jsonError(e.getMessage());
        }
    }

    /**
     * 获取文件/目录信息
     *
     * @param path 文件或目录路径
     * @return 文件信息
     */
    @Tool(description = "获取文件或目录的详细信息，包括大小、创建时间、修改时间、权限等。")
    public String getFileInfo(
            @ToolParam(description = "文件或目录路径") String path) {

        log.info("ℹ️ 获取文件信息: {}", path);

        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return jsonError("路径不存在: " + path);
            }

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", filePath.getFileName().toString());
            info.put("path", filePath.toAbsolutePath().toString());
            info.put("isDirectory", Files.isDirectory(filePath));
            info.put("isRegularFile", Files.isRegularFile(filePath));
            info.put("isReadable", Files.isReadable(filePath));
            info.put("isWritable", Files.isWritable(filePath));
            info.put("size", Files.size(filePath));

            // 时间信息
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            info.put("creationTime", attrs.creationTime().toString());
            info.put("lastModifiedTime", attrs.lastModifiedTime().toString());
            info.put("lastAccessTime", attrs.lastAccessTime().toString());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("fileInfo", info);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 创建目录
     *
     * @param dirPath 目录路径
     * @return 操作结果
     */
    @Tool(description = "创建新目录。如果父目录不存在也会一并创建。")
    public String createDirectory(
            @ToolParam(description = "要创建的目录路径") String dirPath) {

        log.info("📁 创建目录: {}", dirPath);

        try {
            Path path = Paths.get(dirPath);
            Files.createDirectories(path);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("dirPath", dirPath);
            result.put("created", !Files.exists(path) ? "new" : "already exists");

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 删除文件或目录
     *
     * @param path     路径
     * @param recursive 是否递归删除（目录时使用）
     * @return 操作结果
     */
    @Tool(description = "删除指定的文件或目录。需要确认操作，目录默认不递归删除。")
    public String deleteFile(
            @ToolParam(description = "要删除的文件或目录路径") String path,
            @ToolParam(description = "删除目录时是否递归删除子内容，默认false") boolean recursive) {

        log.info("🗑️ 删除: {} (recursive={})", path, recursive);

        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return jsonError("路径不存在: " + path);
            }

            if (Files.isDirectory(filePath)) {
                if (recursive) {
                    Files.walkFileTree(filePath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    Files.delete(filePath);
                }
            } else {
                Files.delete(filePath);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("path", path);
            result.put("deleted", true);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 复制文件
     *
     * @param sourcePath 源文件路径
     * @param destPath   目标文件路径
     * @return 操作结果
     */
    @Tool(description = "复制文件或目录到目标位置。")
    public String copyFile(
            @ToolParam(description = "源文件路径") String sourcePath,
            @ToolParam(description = "目标文件路径") String destPath) {

        log.info("📋 复制文件: {} -> {}", sourcePath, destPath);

        try {
            Path source = Paths.get(sourcePath);
            Path dest = Paths.get(destPath);

            if (!Files.exists(source)) {
                return jsonError("源文件不存在: " + sourcePath);
            }

            Path destDir = dest.getParent();
            if (destDir != null && !Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }

            if (Files.isDirectory(source)) {
                Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path targetDir = dest.resolve(source.relativize(dir));
                        Files.createDirectories(targetDir);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path targetFile = dest.resolve(source.relativize(file));
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("source", sourcePath);
            result.put("destination", destPath);
            result.put("copied", true);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 移动/重命名文件
     *
     * @param sourcePath 源路径
     * @param destPath   目标路径
     * @return 操作结果
     */
    @Tool(description = "移动文件或目录到新位置，也可用于重命名。")
    public String moveFile(
            @ToolParam(description = "源文件路径") String sourcePath,
            @ToolParam(description = "目标文件路径") String destPath) {

        log.info("📦 移动文件: {} -> {}", sourcePath, destPath);

        try {
            Path source = Paths.get(sourcePath);
            Path dest = Paths.get(destPath);

            if (!Files.exists(source)) {
                return jsonError("源文件不存在: " + sourcePath);
            }

            Path destDir = dest.getParent();
            if (destDir != null && !Files.exists(destDir)) {
                Files.createDirectories(destDir);
            }

            Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("source", sourcePath);
            result.put("destination", destPath);
            result.put("moved", true);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 压缩文件或目录
     *
     * @param sourcePath 要压缩的文件或目录路径
     * @param zipPath    压缩文件输出路径
     * @return 操作结果
     */
    @Tool(description = "将文件或目录压缩为 ZIP 格式。")
    public String compressToZip(
            @ToolParam(description = "要压缩的文件或目录路径") String sourcePath,
            @ToolParam(description = "输出的 ZIP 文件路径") String zipPath) {

        log.info("🗜️ 压缩: {} -> {}", sourcePath, zipPath);

        try {
            Path source = Paths.get(sourcePath);
            Path zipFile = Paths.get(zipPath);

            if (!Files.exists(source)) {
                return jsonError("源路径不存在: " + sourcePath);
            }

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                if (Files.isDirectory(source)) {
                    Files.walk(source)
                            .filter(path -> !path.equals(source))
                            .forEach(path -> {
                                try {
                                    ZipEntry entry = new ZipEntry(source.relativize(path).toString());
                                    zos.putNextEntry(entry);
                                    if (Files.isRegularFile(path)) {
                                        Files.copy(path, zos);
                                    }
                                    zos.closeEntry();
                                } catch (IOException e) {
                                    log.error("压缩失败: {}", e.getMessage());
                                }
                            });
                } else {
                    ZipEntry entry = new ZipEntry(source.getFileName().toString());
                    zos.putNextEntry(entry);
                    Files.copy(source, zos);
                    zos.closeEntry();
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("source", sourcePath);
            result.put("zipFile", zipPath);
            result.put("compressed", true);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 解压缩 ZIP 文件
     *
     * @param zipPath   ZIP 文件路径
     * @param destDir   目标目录
     * @return 操作结果
     */
    @Tool(description = "解压缩 ZIP 文件到指定目录。")
    public String extractZip(
            @ToolParam(description = "ZIP 文件路径") String zipPath,
            @ToolParam(description = "目标目录路径") String destDir) {

        log.info("📂 解压: {} -> {}", zipPath, destDir);

        try {
            Path zipFile = Paths.get(zipPath);
            Path targetDir = Paths.get(destDir);

            if (!Files.exists(zipFile)) {
                return jsonError("ZIP 文件不存在: " + zipPath);
            }

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            List<String> extractedFiles = new ArrayList<>();

            try (ZipFile zip = new ZipFile(zipFile.toFile())) {
                zip.entries().asIterator().forEachRemaining(entry -> {
                    try {
                        Path entryPath = targetDir.resolve(entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(entryPath);
                        } else {
                            Files.createDirectories(entryPath.getParent());
                            Files.copy(zip.getInputStream(entry), entryPath);
                            extractedFiles.add(entry.getName());
                        }
                    } catch (IOException e) {
                        log.error("解压失败: {}", e.getMessage());
                    }
                });
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("zipFile", zipPath);
            result.put("destination", destDir);
            result.put("extractedCount", extractedFiles.size());
            result.put("extractedFiles", extractedFiles);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 搜索文件
     *
     * @param dirPath    搜索目录
     * @param fileName   文件名（支持通配符 *）
     * @param maxResults 最大结果数
     * @return 搜索结果
     */
    @Tool(description = "在指定目录中搜索文件。支持通配符匹配，如 *.txt, *.java 等。")
    public String searchFiles(
            @ToolParam(description = "搜索目录路径") String dirPath,
            @ToolParam(description = "文件名模式，如 *.txt, *.java") String fileName,
            @ToolParam(description = "最大返回结果数，默认50") int maxResults) {

        log.info("🔍 搜索文件: {} in {}", fileName, dirPath);

        try {
            Path start = Paths.get(dirPath);
            if (!Files.exists(start) || !Files.isDirectory(start)) {
                return jsonError("搜索目录不存在: " + dirPath);
            }

            String pattern = fileName.replace("*", ".*");
            List<Map<String, String>> results = new ArrayList<>();

            Files.walk(start, Integer.MAX_VALUE)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches(pattern))
                    .limit(maxResults)
                    .forEach(p -> {
                        Map<String, String> fileInfo = new LinkedHashMap<>();
                        fileInfo.put("name", p.getFileName().toString());
                        fileInfo.put("path", p.toAbsolutePath().toString());
                        try {
                            fileInfo.put("size", String.valueOf(Files.size(p)));
                        } catch (IOException e) {
                            fileInfo.put("size", "unknown");
                        }
                        results.add(fileInfo);
                    });

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("searchDir", dirPath);
            result.put("pattern", fileName);
            result.put("foundCount", results.size());
            result.put("files", results);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    /**
     * 获取磁盘空间信息
     *
     * @param path 路径（可以是任意文件或目录）
     * @return 磁盘空间信息
     */
    @Tool(description = "获取指定路径所在磁盘的空间使用情况。")
    public String getDiskSpace(
            @ToolParam(description = "任意文件或目录路径") String path) {

        log.info("💾 获取磁盘空间: {}", path);

        try {
            Path filePath = Paths.get(path).toAbsolutePath().normalize();
            FileStore store = Files.getFileStore(filePath);

            Map<String, Object> info = new LinkedHashMap<>();
            info.put("volume", store.name());
            info.put("totalSpace", formatBytes(store.getTotalSpace()));
            info.put("usableSpace", formatBytes(store.getUsableSpace()));
            info.put("usedSpace", formatBytes(store.getTotalSpace() - store.getUsableSpace()));
            info.put("availableSpace", formatBytes(store.getUsableSpace()));

            double usedPercent = (store.getTotalSpace() - store.getUsableSpace()) * 100.0 / store.getTotalSpace();
            info.put("usedPercent", String.format("%.2f%%", usedPercent));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("diskInfo", info);

            return toJson(result);

        } catch (Exception e) {
            return jsonError(e.getMessage());
        }
    }

    // ========== 辅助方法 ==========

    private Map<String, Object> createFileInfo(Path path, Path basePath) {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            info.put("name", path.getFileName().toString());
            info.put("relativePath", basePath.relativize(path).toString());
            info.put("isDirectory", Files.isDirectory(path));
            info.put("size", Files.size(path));
        } catch (IOException e) {
            info.put("error", e.getMessage());
        }
        return info;
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private String jsonError(String message) {
        return toJson(Map.of("success", false, "error", message));
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{\n");
        int count = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            json.append("  \"").append(entry.getKey()).append("\": ");
            json.append(formatValue(entry.getValue()));
            if (++count < map.size()) json.append(",");
            json.append("\n");
        }
        json.append("}");
        return json.toString();
    }

    private String formatValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String) {
            String str = (String) value;
            str = str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
            return "\"" + str + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof List) return formatList((List<?>) value);
        if (value instanceof Map) return formatMap((Map<?, ?>) value);
        return "\"" + value + "\"";
    }

    private String formatList(List<?> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            sb.append("    ").append(formatValue(list.get(i)));
            if (i < list.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private String formatMap(Map<?, ?> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            sb.append("    \"").append(entry.getKey()).append("\": ");
            sb.append(formatValue(entry.getValue()));
            if (++i < map.size()) sb.append(",");
            sb.append("\n");
        }
        sb.append("  }");
        return sb.toString();
    }
}