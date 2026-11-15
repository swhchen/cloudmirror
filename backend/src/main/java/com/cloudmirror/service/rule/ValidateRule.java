package com.cloudmirror.service.rule;

import java.util.List;

/**
 * 一条可运行性校验规则。
 *
 * <p>加规则 = 新增一个实现并交给 Spring 管理，其它代码不用改。
 */
public interface ValidateRule {

    /** 规则编号，如 R04（决定执行顺序与展示顺序） */
    String code();

    /** 规则名，如 CPU 超配 */
    String name();

    /** 未通过时的级别 */
    Severity severity();

    /** 返回所有"没通过"的项；通过则返回空列表 */
    List<CheckItem> check(ValidateContext ctx);
}
