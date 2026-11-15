package com.cloudmirror.service.rule;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 可运行性校验引擎。
 *
 * <p>纯函数：给定上下文，返回确定结论。不含随机数、不调用模型，同样输入永远同样输出。
 *
 * <p>判定规则：
 * <ul>
 *   <li>存在 ERROR → 不可运行</li>
 *   <li>只有 WARN / INFO → 可运行，但需关注</li>
 *   <li>全部通过 → 可运行</li>
 * </ul>
 * 评分 = 100 - 30×错误 - 10×警告 - 2×提示，下限 0。
 */
@Service
public class ValidateService {

    private static final int PER_ERROR = 30;
    private static final int PER_WARN = 10;
    private static final int PER_INFO = 2;

    private final List<ValidateRule> rules;

    public ValidateService(List<ValidateRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(ValidateRule::code))
                .toList();
    }

    public List<ValidateRule> rules() {
        return rules;
    }

    public ValidateResult validate(ValidateContext ctx) {
        if (ctx.nodes().isEmpty()) {
            return new ValidateResult(false, 0, 0, 0, 0,
                    "工程里还没有节点，先装配再校验", List.of(), List.of());
        }
        List<CheckItem> items = new ArrayList<>();
        for (ValidateRule rule : rules) {
            items.addAll(rule.check(ctx));
        }
        items.sort(Comparator
                .comparing(CheckItem::severity)
                .thenComparing(CheckItem::ruleCode));

        int errors = count(items, Severity.ERROR);
        int warns = count(items, Severity.WARN);
        int infos = count(items, Severity.INFO);

        int score = Math.max(0, 100 - PER_ERROR * errors - PER_WARN * warns - PER_INFO * infos);
        boolean runnable = errors == 0;

        List<ValidateResult.RuleStatus> statuses = rules.stream()
                .map(r -> {
                    int count = (int) items.stream().filter(i -> i.ruleCode().equals(r.code())).count();
                    return new ValidateResult.RuleStatus(r.code(), r.name(), r.severity(), count == 0, count);
                })
                .toList();

        return new ValidateResult(runnable, score, errors, warns, infos,
                summary(runnable, errors, warns, infos), items, statuses);
    }

    private int count(List<CheckItem> items, Severity severity) {
        return (int) items.stream().filter(i -> i.severity() == severity).count();
    }

    private String summary(boolean runnable, int errors, int warns, int infos) {
        if (!runnable) {
            return "不可运行：" + errors + " 个错误"
                    + (warns > 0 ? "、" + warns + " 个警告" : "")
                    + (infos > 0 ? "、" + infos + " 个提示" : "");
        }
        if (warns == 0 && infos == 0) {
            return "全部通过，可运行";
        }
        return "可运行，但有 " + warns + " 个警告、" + infos + " 个提示";
    }
}
