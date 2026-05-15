package com.njhtr.seekai.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 核心 RAG 工具 - 提供代码库的本地索引和语义搜索能力
 */
@Slf4j
@Component
public class CodebaseRagTools {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    // 使用注入的 VectorStore (由 Spring Boot 自动配置为 PgVectorStore)
    public CodebaseRagTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // 使用默认配置，大约每段代码 800 tokens，有少许重叠
        this.textSplitter = new TokenTextSplitter();
    }

    /**
     * 1. 索引整个项目 (indexProject)
     */
    public Function<IndexRequest, IndexResponse> indexProject() {
        return request -> {
            String dirPath = request.dir();
            log.info("🧠 开始构建项目知识库索引：[{}]", dirPath);
            try {
                Path dir = Paths.get(dirPath);
                if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                    return new IndexResponse(false, "Error: 目录不存在或不是文件夹", 0);
                }

                List<Document> documents = new ArrayList<>();

                // 遍历文件并读取
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("node_modules") 
                                  && !p.toString().contains(".git") 
                                  && !p.toString().contains(".idea")
                                  && !p.toString().contains("target")
                                  && !p.toString().endsWith(".jar")
                                  && !p.toString().endsWith(".class"))
                        .forEach(file -> {
                            try {
                                String content = Files.readString(file);
                                if (content.trim().isEmpty()) return;

                                String relativePath = dir.relativize(file).toString();
                                
                                // 获取文件扩展名
                                String extension = "";
                                int i = relativePath.lastIndexOf('.');
                                if (i > 0) {
                                    extension = relativePath.substring(i + 1);
                                }

                                // 构建 Document 对象，带上文件名元数据
                                Document doc = new Document(
                                    content, 
                                    Map.of(
                                        "file_path", relativePath,
                                        "project_dir", dirPath,
                                        "file_extension", extension
                                    )
                                );
                                documents.add(doc);
                            } catch (Exception e) {
                                // 忽略无法读取的二进制文件或编码错误
                            }
                        });
                }

                if (documents.isEmpty()) {
                    return new IndexResponse(true, "扫描完成，但未找到有效代码文件", 0);
                }

                // 切分文档并存入向量库
                log.info("✂️ 正在切分 {} 个代码文件...", documents.size());
                List<Document> chunkedDocs = textSplitter.apply(documents);
                
                log.info("📦 正在生成 Embedding 并存入向量库，共 {} 个片段...", chunkedDocs.size());
                // 如果片段太多，可以考虑分批写入，这里演示简单写入
                vectorStore.add(chunkedDocs);
                
                return new IndexResponse(true, "项目知识库构建成功", chunkedDocs.size());

            } catch (Exception e) {
                log.error("❌ 构建项目索引失败：{}", e.getMessage());
                return new IndexResponse(false, "Exception: " + e.getMessage(), 0);
            }
        };
    }

    /**
     * 2. 语义搜索代码 (semanticSearchCode)
     */
    public Function<SearchRequestDto, SearchResponse> semanticSearchCode() {
        return request -> {
            String query = request.query();
            String projectDir = request.projectDir(); // 新增：指定项目目录过滤
            int topK = request.topK() != null && request.topK() > 0 ? request.topK() : 5;
            
            log.info("🔎 请求语义搜索代码库：项目=[{}], 问题=[{}], 返回前 {} 条", projectDir, query, topK);
            try {
                SearchRequest searchRequest = SearchRequest.builder().query(query).topK(topK).build();
                
                // 如果提供了 projectDir，则使用元数据过滤，避免搜到其他项目的代码
                if (projectDir != null && !projectDir.isEmpty()) {
                    FilterExpressionBuilder b = new FilterExpressionBuilder();
                    searchRequest = SearchRequest.builder().query(query).topK(topK)
                            .filterExpression(b.eq("project_dir", projectDir).build()).build();
                }

                List<Document> results = vectorStore.similaritySearch(searchRequest);

                if (results.isEmpty()) {
                    return new SearchResponse(true, "知识库中未找到相关代码片段。请确保已经调用过 indexProject", "");
                }

                // 拼装结果，带上文件路径和相似度内容
                String output = results.stream().map(doc -> {
                    String path = String.valueOf(doc.getMetadata().get("file_path"));
                    return String.format("--- File: %s ---\n%s\n", path, doc.getText());
                }).collect(Collectors.joining("\n\n"));

                return new SearchResponse(true, "找到相关代码片段", output);

            } catch (Exception e) {
                log.error("❌ 语义搜索失败：{}", e.getMessage());
                return new SearchResponse(false, "Exception: " + e.getMessage(), "");
            }
        };
    }

    // ========== DTOs ==========

    public record IndexRequest(String dir) {}
    public record IndexResponse(boolean success, String message, int chunksIndexed) {}

    public record SearchRequestDto(String projectDir, String query, Integer topK) {}
    public record SearchResponse(boolean success, String message, String snippets) {}
}
