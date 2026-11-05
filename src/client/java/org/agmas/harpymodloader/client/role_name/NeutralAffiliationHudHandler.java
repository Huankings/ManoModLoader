package org.agmas.harpymodloader.client.role_name;

import dev.doctor4t.wathe.api.client.gui.RoleNameHudApi;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategory;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategoryApi;

/**
 * 把 Harpy 中立细分类转换为 Wathe 的准心阵营行。
 */
public final class NeutralAffiliationHudHandler {
    private NeutralAffiliationHudHandler() {
    }

    public static void register() {
        RoleNameHudApi.registerAffiliationLine(
                Identifier.of(Harpymodloader.MOD_ID, "role_name/neutral_affiliation"),
                RoleNameHudApi.DEFAULT_PRIORITY + 100,
                context -> {
                    if (!GameFunctions.isPlayerSpectatingOrCreative(context.viewer())) {
                        return null;
                    }
                    NeutralRoleCategory category = NeutralRoleCategoryApi.resolveCategory(context.targetRole());
                    return category == null ? null : new RoleNameHudApi.RoleNameLine(
                            Text.translatable(category.translationKey()),
                            category.displayColor()
                    );
                }
        );
    }
}
