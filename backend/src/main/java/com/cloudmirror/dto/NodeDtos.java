package com.cloudmirror.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 节点、端口、画布坐标相关 DTO */
public final class NodeDtos {

    private NodeDtos() {
    }

    public record PortReq(Integer port,
                          String protocol,
                          String scope,
                          Integer hostPort,
                          String purpose) {
    }

    public record PortVO(Long id,
                         Integer port,
                         String protocol,
                         String scope,
                         Integer hostPort,
                         String purpose) {
    }

    public record SaveReq(@NotBlank(message = "节点名不能为空") @Size(max = 100) String name,
                          @NotBlank(message = "节点类型不能为空") String nodeKind,
                          Long modelId,
                          BigDecimal cpuCores,
                          BigDecimal cpuRequest,
                          Integer memTotalMb,
                          Integer memRequestMb,
                          BigDecimal diskGb,
                          Integer replicas,
                          String ip,
                          String runtime,
                          String status,
                          Double posX,
                          Double posY,
                          String description,
                          List<PortReq> ports) {
    }

    public record VO(Long id,
                     Long projectId,
                     String name,
                     String nodeKind,
                     Long modelId,
                     String modelName,
                     BigDecimal cpuCores,
                     BigDecimal cpuRequest,
                     Integer memTotalMb,
                     Integer memRequestMb,
                     BigDecimal diskGb,
                     Integer replicas,
                     String ip,
                     String runtime,
                     String status,
                     Double posX,
                     Double posY,
                     String description,
                     List<PortVO> ports) {
    }

    public record Position(Long id, Double x, Double y) {
    }

    public record PositionsReq(@NotEmpty(message = "坐标列表不能为空") List<Position> positions) {
    }
}
