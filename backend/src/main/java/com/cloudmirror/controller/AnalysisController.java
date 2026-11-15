package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.AnalysisDtos;
import com.cloudmirror.dto.PageDtos;
import com.cloudmirror.service.AnalysisRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 传播推演：执行、历史列表、详情回看 */
@RestController
@RequestMapping("/api/projects/{projectId}/analyses")
public class AnalysisController {

    private final AnalysisRunner analysisRunner;

    public AnalysisController(AnalysisRunner analysisRunner) {
        this.analysisRunner = analysisRunner;
    }

    @PostMapping
    public Result<AnalysisDtos.Response> analyze(@PathVariable Long projectId,
                                                 @RequestParam(required = false) Integer windowSeconds) {
        return Result.ok(analysisRunner.analyze(projectId, windowSeconds));
    }

    @GetMapping
    public Result<PageDtos<AnalysisDtos.HistoryItem>> list(@PathVariable Long projectId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "10") int size) {
        return Result.ok(analysisRunner.list(projectId, page, size));
    }

    @GetMapping("/{analysisId}")
    public Result<AnalysisDtos.Detail> detail(@PathVariable Long projectId,
                                              @PathVariable Long analysisId) {
        return Result.ok(analysisRunner.detail(projectId, analysisId));
    }
}
