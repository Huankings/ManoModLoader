package org.agmas.harpymodloader.api.assignment;

/**
 * Harpy 扩展职业替换流程中的分配阶段。
 *
 * <p>阶段边界很重要：同局排斥、成对生成这类规则通常只想观察某一个替换池内已经落地的职业，
 * 而不是把上一局或其它阵营阶段的状态混进来。</p>
 */
public enum RoleAssignmentPhase {
    /**
     * 平民位替换阶段，同时包含 Harpy 当前把中立职业塞进平民基底的那一段逻辑。
     */
    CIVILIAN_REPLACEMENT,
    /**
     * 义警位替换阶段。
     */
    VIGILANTE_REPLACEMENT,
    /**
     * 杀手位替换阶段。
     */
    KILLER_REPLACEMENT
}
