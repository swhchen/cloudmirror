package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.NodeDtos;
import com.cloudmirror.service.NodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 节点 CRUD、端口随节点提交、批量保存坐标 */
@RestController
@RequestMapping("/api/projects/{projectId}/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @GetMapping
    public Result<List<NodeDtos.VO>> list(@PathVariable Long projectId) {
        return Result.ok(nodeService.list(projectId));
    }

    @PostMapping
    public Result<NodeDtos.VO> create(@PathVariable Long projectId,
                                      @Valid @RequestBody NodeDtos.SaveReq req) {
        return Result.ok(nodeService.create(projectId, req));
    }

    @PutMapping("/{nodeId}")
    public Result<NodeDtos.VO> update(@PathVariable Long projectId,
                                      @PathVariable Long nodeId,
                                      @RequestParam(defaultValue = "false") boolean force,
                                      @Valid @RequestBody NodeDtos.SaveReq req) {
        return Result.ok(nodeService.update(projectId, nodeId, req, force));
    }

    @DeleteMapping("/{nodeId}")
    public Result<Void> delete(@PathVariable Long projectId, @PathVariable Long nodeId) {
        nodeService.delete(projectId, nodeId);
        return Result.ok();
    }

    @PutMapping("/positions")
    public Result<Void> positions(@PathVariable Long projectId,
                                  @Valid @RequestBody NodeDtos.PositionsReq req) {
        nodeService.savePositions(projectId, req);
        return Result.ok();
    }
}
