package com.cloudmirror;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 云镜推演 · 后端入口
 *
 * <p>启动方式：
 * <ul>
 *   <li>IDEA：直接运行本类</li>
 *   <li>命令行：java -jar target/cloudmirror.jar</li>
 *   <li>无 MySQL 联调：--spring.profiles.active=h2</li>
 * </ul>
 */
@SpringBootApplication
@MapperScan("com.cloudmirror.mapper")
public class CloudMirrorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudMirrorApplication.class, args);
    }
}
