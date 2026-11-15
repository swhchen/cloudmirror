package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 根路径提示页：直接访问 8080 时告诉你去哪儿。
 */
@RestController
public class IndexController {

    @GetMapping("/")
    public Result<Map<String, String>> index() {
        Map<String, String> links = new LinkedHashMap<>();
        links.put("app", "云镜推演（CloudMirror Deduction）");
        links.put("health", "/api/health");
        links.put("swagger", "/swagger-ui.html");
        links.put("apiDocs", "/v3/api-docs");
        return Result.ok(links);
    }
}
