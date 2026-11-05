package org.agmas.harpymodloader.api.neutral;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 一个可由扩展注册的“中立细分类”定义。
 *
 * <p>Wathe 的 {@code Faction.NEUTRAL} 仍然是职业分配、结算和权重使用的基础阵营；
 * 本类型只在 Harpy 扩展层继续区分杀手中立、普通中立、独立中立或其它自定义中立。
 * 分类默认只提供名称与颜色，两个杀手侧能力必须由注册者显式开启，避免自定义分类
 * 因为“也是中立”就意外泄漏给杀手。</p>
 */
public record NeutralRoleCategory(@NotNull Identifier id,
                                  @NotNull String translationKey,
                                  int displayColor,
                                  boolean killerInstinctRevealsRoleColor,
                                  boolean showsAsKillerCohortTarget) {
    public NeutralRoleCategory {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(translationKey, "translationKey");
        displayColor &= 0x00FFFFFF;
    }

    /**
     * 创建只提供分类字样与颜色、没有杀手侧额外能力的中立分类。
     */
    public static @NotNull NeutralRoleCategory displayOnly(@NotNull Identifier id,
                                                           @NotNull String translationKey,
                                                           int displayColor) {
        return new NeutralRoleCategory(id, translationKey, displayColor, false, false);
    }
}
