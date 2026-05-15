package com.njhtr.seekai.controller;

import com.njhtr.seekai.dto.ApiResponse;
import com.njhtr.seekai.service.BingSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 搜索测试控制器 - 直接测试必应搜索功能
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchTestController {
    
    private final BingSearchService searchService;
    
    /**
     * 测试搜索接口
     */
    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> testSearch(@RequestParam String q) {
        log.info("🔍 测试搜索：{}", q);
        
        // 执行搜索
        BingSearchService.SearchResults results = searchService.search(q, 5);
        
        // 格式化输出
        String output = results.toTextSummary();
        
        return ResponseEntity.ok(ApiResponse.success(output));
    }
}
