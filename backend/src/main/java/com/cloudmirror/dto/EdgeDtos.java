package com.cloudmirror.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 连线 DTO */
public final class EdgeDtos {

    private EdgeDtos() {
    }

    public record SaveReq(@NotBlank(message = "连线类型不能为空") String edgeKind,
                          @NotNull(message = "源节点不能为空") Long sourceNodeId,
                          @NotNull(message = "目标节点不能为空") Long targetNodeId,
                          Long sourcePortId,
                          Long targetPortId,
                          Integer bandwidthMbps,
                          Integer latencyMs,
                          Boolean strong,
                          BigDecimal weight,
                          @Size(max = 300) String description) {
    }

    public record VO(Long id,
                     String edgeKind,
                     Long sourceNodeId,
                     Long targetNodeId,
                     Long sourcePortId,
                     Long targetPortId,
                     Integer bandwidthMbps,
                     Integer latencyMs,
                     Boolean strong,
                     BigDecimal weight,
                     String description) {
    }
}
