package com.cloudmirror.dto;

import com.cloudmirror.service.propagation.PropagationResult;

import java.time.LocalDateTime;
import java.util.List;

/** 传播分析 DTO */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    /** 一次分析：分析 ID + 算法完整结论 */
    public record Response(Long analysisId, PropagationResult result) {
    }

    /** 历史列表项 */
    public record HistoryItem(Long id,
                              LocalDateTime analysisTime,
                              String summary,
                              Integer faultSourceCount,
                              Integer directCount,
                              Integer indirectCount,
                              Integer totalAffectedCount,
                              Double impactRatio,
                              Integer windowSeconds,
                              String cascadeSummary) {
    }

    /** 历史详情：故障源快照 + 受影响节点（含路径） */
    public record Detail(Long id,
                         LocalDateTime analysisTime,
                         String summary,
                         Integer faultSourceCount,
                         Integer directCount,
                         Integer indirectCount,
                         Integer totalNodeCount,
                         Double impactRatio,
                         Integer windowSeconds,
                         String cascadeSummary,
                         List<SourceVO> faultSources,
                         List<AffectedVO> affectedNodes) {
    }

    public record SourceVO(Long faultId, Long nodeId, String nodeName, String faultType, String severity) {
    }

    public record AffectedVO(Long nodeId,
                             String nodeName,
                             String impactLevel,
                             Integer distance,
                             Long sourceFaultId,
                             Long sourceNodeId,
                             List<StepVO> path) {
    }

    public record StepVO(Integer stepIndex,
                         Long nodeId,
                         String nodeName,
                         Long edgeId,
                         Long callerPort,
                         Long calleePort) {
    }
}
