package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.InsightDtos;
import com.cloudmirror.service.InsightRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 增强分析：关键单点 / 隔离切口 / 多动作恢复序列 */
@RestController
@RequestMapping("/api/projects/{projectId}/insights")
public class InsightController {

    private final InsightRunner insightRunner;

    public InsightController(InsightRunner insightRunner) {
        this.insightRunner = insightRunner;
    }

    @GetMapping
    public Result<InsightDtos.Response> insights(@PathVariable Long projectId) {
        return Result.ok(insightRunner.analyze(projectId));
    }
}
