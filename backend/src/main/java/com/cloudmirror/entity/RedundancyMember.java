package com.cloudmirror.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 冗余组成员 */
@TableName("redundancy_member")
public class RedundancyMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private Long nodeId;
    private String memberRole;
    private Integer priority;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long v) { this.groupId = v; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long v) { this.nodeId = v; }
    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String v) { this.memberRole = v; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer v) { this.priority = v; }
}
