package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.RecoveryDtos;
import com.cloudmirror.service.RecoveryRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 恢复策略推荐 */
@RestController
@RequestMapping("/api/projects/{projectId}/recovery")
public class RecoveryController {

    private final RecoveryRunner recoveryRunner;

    public RecoveryController(RecoveryRunner recoveryRunner) {
        this.recoveryRunner = recoveryRunner;
    }

    @PostMapping("/recommend")
    public Result<RecoveryDtos.Response> recommend(@PathVariable Long projectId,
                                                   @RequestParam(required = false) Long analysisId) {
        return Result.ok(recoveryRunner.recommend(projectId, analysisId));
    }

    @GetMapping("/plans")
    public Result<List<RecoveryDtos.PlanVO>> plans(@PathVariable Long projectId,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(recoveryRunner.listPlans(projectId, limit));
    }
}
