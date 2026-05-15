package com.njhtr.seekai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 必应搜索服务 (现已升级为基于 SearXNG 的聚合搜索)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BingSearchService {
    
    // 指向您在服务器上部署的 SearXNG 实例
    private static final String SEARXNG_URL = "http://8.134.23.170:38471/search?format=json&q=";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 搜索并提取结果
     */
    public SearchResults search(String query, int maxResults) {
        try {
            // URL 编码查询词
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = SEARXNG_URL + encodedQuery;
            
            log.info("🔍 正在通过 SearXNG 搜索：{}", query);
            
            // 发送 HTTP 请求获取 JSON
            String jsonResponse = restTemplate.getForObject(searchUrl, String.class);
            
            if (jsonResponse == null) {
                log.warn("SearXNG 返回为空");
                return new SearchResults(query, List.of());
            }

            // 解析 JSON 结果
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode resultsNode = root.path("results");
            
            List<SearchResult> results = new ArrayList<>();
            if (resultsNode.isArray()) {
                int count = 0;
                for (JsonNode node : resultsNode) {
                    if (count >= maxResults) break;
                    
                    String title = node.path("title").asText("");
                    String link = node.path("url").asText("");
                    String snippet = node.path("content").asText("");
                    
                    if (!link.isEmpty()) {
                        results.add(new SearchResult(title, link, snippet, ""));
                        count++;
                    }
                }
            }
            
            log.info("✅ 搜索完成，找到 {} 条结果", results.size());
            
            // 注意：暂时关闭深度抓取网页正文，先确保搜索结果能正常返回。SearXNG 的 snippet 通常已经比较丰富。
            return new SearchResults(query, results);
            
        } catch (Exception e) {
            log.error("❌ 搜索失败：{}", e.getMessage(), e);
            return new SearchResults(query, List.of());
        }
    }
    
    // 已废弃：旧版基于 Jsoup 的解析
    /*
    private List<SearchResult> extractSearchResults(Document doc, int maxResults) {
        ...
    }
    */
    
    // 已废弃：基于 Jsoup 的网页正文提取
    /*
    private String scrapeContent(String url) {
        ...
    }
    */
    
    /**
     * 搜索结果封装类
     */
    public record SearchResults(String query, List<SearchResult> results) {
        
        /**
         * 格式化为文本输出
         */
        public String toTextSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("🔍 搜索结果：").append(query).append("\n\n");
            
            if (results.isEmpty()) {
                sb.append("未找到相关结果。\n");
                return sb.toString();
            }
            
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                sb.append(i + 1).append(". ").append(result.title()).append("\n");
                sb.append("   链接：").append(result.link()).append("\n");
                if (!result.snippet().isEmpty()) {
                    sb.append("   摘要：").append(result.snippet()).append("\n");
                }
                if (result.content() != null && !result.content().isEmpty()) {
                    sb.append("   正文片段：").append(result.content()).append("\n");
                }
                sb.append("\n");
            }
            
            return sb.toString();
        }
    }
    
    /**
     * 单个搜索结果
     */
    public record SearchResult(String title, String link, String snippet, String content) {
    }
}
