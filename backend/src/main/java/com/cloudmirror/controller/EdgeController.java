package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.EdgeDtos;
import com.cloudmirror.service.EdgeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 连线 CRUD（含端口端点） */
@RestController
@RequestMapping("/api/projects/{projectId}/edges")
public class EdgeController {

    private final EdgeService edgeService;

    public EdgeController(EdgeService edgeService) {
        this.edgeService = edgeService;
    }

    @GetMapping
    public Result<List<EdgeDtos.VO>> list(@PathVariable Long projectId) {
        return Result.ok(edgeService.list(projectId));
    }

    @PostMapping
    public Result<EdgeDtos.VO> create(@PathVariable Long projectId,
                                      @Valid @RequestBody EdgeDtos.SaveReq req) {
        return Result.ok(edgeService.create(projectId, req));
    }

    @DeleteMapping("/{edgeId}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long edgeId) {
        edgeService.delete(projectId, edgeId);
        return Result.ok();
    }
}
