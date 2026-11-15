package com.cloudmirror.controller;

import com.cloudmirror.common.Result;
import com.cloudmirror.dto.ValidateDtos;
import com.cloudmirror.service.ValidationRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 可运行性校验：执行一次、回看最近一次 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ValidateController {

    private final ValidationRunner validationRunner;

    public ValidateController(ValidationRunner validationRunner) {
        this.validationRunner = validationRunner;
    }

    @PostMapping("/validate")
    public Result<ValidateDtos.Response> validate(@PathVariable Long projectId) {
        return Result.ok(validationRunner.validate(projectId));
    }

    @GetMapping("/validations/latest")
    public Result<ValidateDtos.LatestVO> latest(@PathVariable Long projectId) {
        return Result.ok(validationRunner.latest(projectId));
    }
}
