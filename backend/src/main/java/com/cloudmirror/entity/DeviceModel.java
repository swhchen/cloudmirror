package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/** 型号库：节点选型号后自动带出资源，带出后仍可手改 */
@TableName("asm_device_model")
public class DeviceModel {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String kind;
    private String modelName;
    private BigDecimal cpuCores;
    private Integer memMb;
    private BigDecimal diskGb;
    private String specJson;
    private Boolean builtin;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public BigDecimal getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(BigDecimal cpuCores) {
        this.cpuCores = cpuCores;
    }

    public Integer getMemMb() {
        return memMb;
    }

    public void setMemMb(Integer memMb) {
        this.memMb = memMb;
    }

    public BigDecimal getDiskGb() {
        return diskGb;
    }

    public void setDiskGb(BigDecimal diskGb) {
        this.diskGb = diskGb;
    }

    public String getSpecJson() {
        return specJson;
    }

    public void setSpecJson(String specJson) {
        this.specJson = specJson;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }
}
