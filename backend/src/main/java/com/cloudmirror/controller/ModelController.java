package com.cloudmirror.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudmirror.common.Result;
import com.cloudmirror.entity.DeviceModel;
import com.cloudmirror.mapper.DeviceModelMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 型号库查询（内置 16 个规格，可按节点类型过滤） */
@RestController
@RequestMapping("/api/node-models")
public class ModelController {

    private final DeviceModelMapper modelMapper;

    public ModelController(DeviceModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @GetMapping
    public Result<List<DeviceModel>> list(@RequestParam(required = false) String kind) {
        var wrapper = Wrappers.<DeviceModel>lambdaQuery();
        if (kind != null && !kind.isBlank()) {
            wrapper.eq(DeviceModel::getKind, kind);
        }
        wrapper.orderByAsc(DeviceModel::getId);
        return Result.ok(modelMapper.selectList(wrapper));
    }
}
