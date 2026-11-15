package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.FaultDtos;
import com.cloudmirror.service.FaultService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 故障注入 / 查询 / 恢复 */
@RestController
@RequestMapping("/api/projects/{projectId}/faults")
public class FaultController {

    private final FaultService faultService;

    public FaultController(FaultService faultService) {
        this.faultService = faultService;
    }

    @GetMapping
    public Result<List<FaultDtos.VO>> list(@PathVariable Long projectId) {
        return Result.ok(faultService.list(projectId));
    }

    @PostMapping
    public Result<FaultDtos.VO> inject(@PathVariable Long projectId,
                                       @Valid @RequestBody FaultDtos.InjectReq req) {
        return Result.ok(faultService.inject(projectId, req));
    }

    @PostMapping("/{faultId}/recover")
    public Result<FaultDtos.VO> recover(@PathVariable Long projectId, @PathVariable Long faultId) {
        return Result.ok(faultService.recover(projectId, faultId));
    }
}
