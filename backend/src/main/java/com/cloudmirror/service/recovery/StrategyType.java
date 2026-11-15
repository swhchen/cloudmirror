package com.cloudmirror.service.recovery;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 三类恢复策略。
 *
 * <p>顺序即同分时的推荐优先级：重启 → 切备 → 隔离。
 */
public enum StrategyType {

    RESTART_SERVICE("restart_service", "服务重启"),
    FAILOVER("failover", "备用节点切换"),
    ISOLATE_NODE("isolate_node", "故障节点隔离");

    private final String code;
    private final String label;

    StrategyType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 对外 JSON 用 code（restart_service / failover / isolate_node），不是枚举名 */
    @JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
