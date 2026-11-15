package com.cloudmirror.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档：启动后访问 /swagger-ui.html 可在线调试所有接口。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cloudMirrorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("云镜推演 API")
                .version("0.1.0")
                .description("面向云原生系统的故障传播分析与恢复决策平台"));
    }
}
