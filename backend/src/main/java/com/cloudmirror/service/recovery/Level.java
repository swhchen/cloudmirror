package com.cloudmirror.service.recovery;

/**
 * 成本 / 风险等级：低 1.0、中 0.6、高 0.3（越高越贵 / 越险，得分越低）。
 */
public enum Level {

    LOW("低", 1.0),
    MEDIUM("中", 0.6),
    HIGH("高", 0.3);

    private final String label;
    private final double score;

    Level(String label, double score) {
        this.label = label;
        this.score = score;
    }

    public String label() {
        return label;
    }

    public double score() {
        return score;
    }
}
