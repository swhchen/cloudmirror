package com.cloudmirror.dto;

/** AI 诊断返回结构 */
public final class AiDtos {

    private AiDtos() {
    }

    /**
     * @param source  llm / fallback / local-fallback
     * @param online  是否真的调用了 Python AI 服务
     */
    public record Response(String source, String content, boolean online) {
    }
}
