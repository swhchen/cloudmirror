package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.ProjectDtos;
import com.cloudmirror.service.ProjectService;
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

/** 工程 CRUD + 画布视角保存 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Result<List<ProjectDtos.VO>> list() {
        return Result.ok(projectService.list());
    }

    @PostMapping
    public Result<ProjectDtos.VO> create(@Valid @RequestBody ProjectDtos.CreateReq req) {
        return Result.ok(projectService.create(req));
    }

    @GetMapping("/{id}")
    public Result<ProjectDtos.VO> get(@PathVariable Long id) {
        return Result.ok(projectService.get(id));
    }

    @PutMapping("/{id}")
    public Result<ProjectDtos.VO> update(@PathVariable Long id,
                                         @Valid @RequestBody ProjectDtos.UpdateReq req) {
        return Result.ok(projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/view")
    public Result<ProjectDtos.VO> viewport(@PathVariable Long id,
                                           @RequestBody ProjectDtos.ViewportReq req) {
        return Result.ok(projectService.updateViewport(id, req));
    }
}
