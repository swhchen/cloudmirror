package com.cloudmirror.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 故障注入 DTO */
public final class FaultDtos {

    private FaultDtos() {
    }

    public record InjectReq(@NotNull(message = "节点不能为空") Long nodeId,
                            @NotBlank(message = "故障类型不能为空") String faultType,
                            @NotBlank(message = "严重程度不能为空") String severity,
                            @Size(max = 500) String description) {
    }

    public record VO(Long id,
                     Long nodeId,
                     String nodeName,
                     String faultType,
                     String severity,
                     String description,
                     String status,
                     LocalDateTime createdAt,
                     LocalDateTime recoveredAt) {
    }
}
