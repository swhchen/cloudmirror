package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.RedundancyDtos;
import com.cloudmirror.service.RedundancyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 冗余组 CRUD（主备 / 双活 / 手动备用） */
@RestController
@RequestMapping("/api/projects/{projectId}/redundancy-groups")
public class RedundancyController {

    private final RedundancyService redundancyService;

    public RedundancyController(RedundancyService redundancyService) {
        this.redundancyService = redundancyService;
    }

    @GetMapping
    public Result<List<RedundancyDtos.VO>> list(@PathVariable Long projectId) {
        return Result.ok(redundancyService.list(projectId));
    }

    @PostMapping
    public Result<RedundancyDtos.VO> create(@PathVariable Long projectId,
                                            @Valid @RequestBody RedundancyDtos.SaveReq req) {
        return Result.ok(redundancyService.create(projectId, req));
    }

    @PutMapping("/{groupId}")
    public Result<RedundancyDtos.VO> update(@PathVariable Long projectId,
                                            @PathVariable Long groupId,
                                            @Valid @RequestBody RedundancyDtos.SaveReq req) {
        return Result.ok(redundancyService.update(projectId, groupId, req));
    }

    @DeleteMapping("/{groupId}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long groupId) {
        redundancyService.delete(projectId, groupId);
        return Result.ok();
    }
}
