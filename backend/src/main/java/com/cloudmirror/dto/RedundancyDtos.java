package com.cloudmirror.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 冗余组 DTO */
public final class RedundancyDtos {

    private RedundancyDtos() {
    }

    public record MemberReq(Long nodeId, String role, Integer priority) {
    }

    public record SaveReq(@NotBlank(message = "冗余组名不能为空") @Size(max = 100) String name,
                          @NotBlank(message = "冗余模式不能为空") String redundancyType,
                          @Size(max = 500) String description,
                          @NotEmpty(message = "成员不能为空") List<MemberReq> members) {
    }

    public record MemberVO(Long id, Long nodeId, String nodeName, String role, Integer priority) {
    }

    public record VO(Long id,
                     String name,
                     String redundancyType,
                     String description,
                     List<MemberVO> members) {
    }
}
