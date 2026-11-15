package com.cloudmirror.dto;

import com.cloudmirror.service.analysis.CascadeAnalyzer;
import com.cloudmirror.service.analysis.CriticalNodeAnalyzer;
import com.cloudmirror.service.analysis.IsolationCutter;
import com.cloudmirror.service.recovery.RecoveryPlanner;

import java.util.List;

/** 增强分析（E2 割点 / E3 隔离切口 / E4 恢复序列 / 级联失效）的返回结构 */
public final class InsightDtos {

    private InsightDtos() {
    }

    public record Response(List<CriticalNodeAnalyzer.CriticalNode> criticalNodes,
                           IsolationCutter.CutResult isolation,
                           RecoveryPlanner.PlannerResult sequence,
                           CascadeAnalyzer.CascadeResult cascade,
                           String note) {
    }
}
