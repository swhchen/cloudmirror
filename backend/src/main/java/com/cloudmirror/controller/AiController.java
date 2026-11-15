package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.AiDtos;
import com.cloudmirror.service.AiRunner;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 辅助诊断：调用 Python 服务，失败自动降级 */
@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiController {

    private final AiRunner aiRunner;

    public AiController(AiRunner aiRunner) {
        this.aiRunner = aiRunner;
    }

    @PostMapping("/diagnose")
    public Result<AiDtos.Response> diagnose(@PathVariable Long projectId) {
        return Result.ok(aiRunner.diagnose(projectId));
    }
}
