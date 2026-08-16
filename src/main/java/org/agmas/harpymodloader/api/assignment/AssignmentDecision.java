package org.agmas.harpymodloader.api.assignment;

/**
 * Harpy 分配规则的统一判定结果。
 *
 * <p>PASS 表示当前规则不处理，继续交给后续规则和 Harpy 默认分配逻辑；
 * DENY 表示当前职业/词条本次不允许分配。这里不提供 ALLOW，是为了避免高优先级规则
 * 误把低优先级的硬性禁用覆盖掉，扩展需要“强制补位”时应使用阶段回调自己写入最终结果。</p>
 */
public enum AssignmentDecision {
    PASS,
    DENY;

    public boolean denied() {
        return this == DENY;
    }
}
