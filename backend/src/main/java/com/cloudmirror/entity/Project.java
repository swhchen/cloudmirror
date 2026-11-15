package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 推演工程：一张画布 = 一个工程 */
@TableName("project")
public class Project {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String name;
    private String description;
    private BigDecimal viewScale;
    private Double viewOffsetX;
    private Double viewOffsetY;
    private Boolean gridSnap;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getViewScale() {
        return viewScale;
    }

    public void setViewScale(BigDecimal viewScale) {
        this.viewScale = viewScale;
    }

    public Double getViewOffsetX() {
        return viewOffsetX;
    }

    public void setViewOffsetX(Double viewOffsetX) {
        this.viewOffsetX = viewOffsetX;
    }

    public Double getViewOffsetY() {
        return viewOffsetY;
    }

    public void setViewOffsetY(Double viewOffsetY) {
        this.viewOffsetY = viewOffsetY;
    }

    public Boolean getGridSnap() {
        return gridSnap;
    }

    public void setGridSnap(Boolean gridSnap) {
        this.gridSnap = gridSnap;
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
