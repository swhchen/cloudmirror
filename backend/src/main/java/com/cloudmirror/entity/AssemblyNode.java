package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 装配节点 */
@TableName("asm_node")
public class AssemblyNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long modelId;
    private String name;
    private String nodeKind;
    private BigDecimal cpuCores;
    private BigDecimal cpuRequest;
    private Integer memTotalMb;
    private Integer memRequestMb;
    private BigDecimal diskGb;
    private Integer replicas;
    private String ip;
    private String runtime;
    private String status;
    private Double posX;
    private Double posY;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNodeKind() {
        return nodeKind;
    }

    public void setNodeKind(String nodeKind) {
        this.nodeKind = nodeKind;
    }

    public BigDecimal getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(BigDecimal cpuCores) {
        this.cpuCores = cpuCores;
    }

    public BigDecimal getCpuRequest() {
        return cpuRequest;
    }

    public void setCpuRequest(BigDecimal cpuRequest) {
        this.cpuRequest = cpuRequest;
    }

    public Integer getMemTotalMb() {
        return memTotalMb;
    }

    public void setMemTotalMb(Integer memTotalMb) {
        this.memTotalMb = memTotalMb;
    }

    public Integer getMemRequestMb() {
        return memRequestMb;
    }

    public void setMemRequestMb(Integer memRequestMb) {
        this.memRequestMb = memRequestMb;
    }

    public BigDecimal getDiskGb() {
        return diskGb;
    }

    public void setDiskGb(BigDecimal diskGb) {
        this.diskGb = diskGb;
    }

    public Integer getReplicas() {
        return replicas;
    }

    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getPosX() {
        return posX;
    }

    public void setPosX(Double posX) {
        this.posX = posX;
    }

    public Double getPosY() {
        return posY;
    }

    public void setPosY(Double posY) {
        this.posY = posY;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
