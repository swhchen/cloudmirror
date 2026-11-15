package com.cloudmirror.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 工程相关 DTO */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateReq(@NotBlank(message = "工程名不能为空") @Size(max = 100) String name,
                            @Size(max = 500) String description) {
    }

    public record UpdateReq(@Size(max = 100) String name,
                            @Size(max = 500) String description) {
    }

    public record ViewportReq(Double scale, Double offsetX, Double offsetY, Boolean gridSnap) {
    }

    public record VO(Long id,
                     String name,
                     String description,
                     Double viewScale,
                     Double viewOffsetX,
                     Double viewOffsetY,
                     Boolean gridSnap,
                     long nodeCount,
                     long edgeCount,
                     LocalDateTime createdAt,
                     LocalDateTime updatedAt) {
    }
}
