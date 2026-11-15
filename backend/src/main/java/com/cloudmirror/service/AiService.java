package com.cloudmirror.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 调用 Python AI 服务（FastAPI，默认 8010）。
 *
 * <p>连不上 / 超时 / 非 200 一律返回空，由上层降级为本地规则诊断——AI 挂了不影响主流程。
 */
@Service
public class AiService {

    private final ObjectMapper mapper;
    private final String baseUrl;
    private final HttpClient client;

    public AiService(ObjectMapper mapper,
                     @Value("${cloudmirror.ai.base-url:http://127.0.0.1:8010}") String baseUrl) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)   // uvicorn 不支持 h2c 升级
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public Optional<AiResult> diagnose(Map<String, Object> payload) {
        try {
            String body = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/ai/diagnose"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode node = mapper.readTree(response.body());
            String content = node.path("content").asText("");
            if (content.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AiResult(node.path("source").asText("fallback"), content));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record AiResult(String source, String content) {
    }
}
