package org.agmas.harpymodloader.api.neutral;

import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;

/**
 * Harpy 内置中立细分类 ID 与定义。
 */
public final class NeutralRoleCategories {
    public static final Identifier KILLER_NEUTRAL_ID = Identifier.of(Harpymodloader.MOD_ID, "killer_neutral");
    public static final Identifier ORDINARY_NEUTRAL_ID = Identifier.of(Harpymodloader.MOD_ID, "ordinary_neutral");
    public static final Identifier INDEPENDENT_NEUTRAL_ID = Identifier.of(Harpymodloader.MOD_ID, "independent_neutral");

    public static final NeutralRoleCategory KILLER_NEUTRAL = new NeutralRoleCategory(
            KILLER_NEUTRAL_ID,
            "hud.harpymodloader.role_name.affiliation.killer_neutral",
            0xC80096,
            true,
            true
    );
    public static final NeutralRoleCategory ORDINARY_NEUTRAL = NeutralRoleCategory.displayOnly(
            ORDINARY_NEUTRAL_ID,
            "hud.harpymodloader.role_name.affiliation.ordinary_neutral",
            0x1e00a0
    );
    public static final NeutralRoleCategory INDEPENDENT_NEUTRAL = NeutralRoleCategory.displayOnly(
            INDEPENDENT_NEUTRAL_ID,
            "hud.harpymodloader.role_name.affiliation.independent_neutral",
            0x4B00BE
    );

    private NeutralRoleCategories() {
    }
}
