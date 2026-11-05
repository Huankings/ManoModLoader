package org.agmas.harpymodloader.client.instinct.killer;

import dev.doctor4t.wathe.api.instinct.InstinctApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategory;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategoryApi;

/**
 * 杀手查看声明了对应能力的中立分类时，使用目标真实职业色描边。
 */
public final class KillerNeutralInstinctHandler {
    private static final int PRIORITY_KILLER_NEUTRAL_ROLE_COLOR = 100;

    private KillerNeutralInstinctHandler() {
    }

    public static void register() {
        InstinctApi.registerHighlight(
                Identifier.of(Harpymodloader.MOD_ID, "killer_neutral_role_color"),
                PRIORITY_KILLER_NEUTRAL_ROLE_COLOR,
                (viewer, target) -> {
                    if (!(target instanceof PlayerEntity targetPlayer)
                            || !WatheClient.isInstinctEnabled()
                            || !GameFunctions.isPlayerAliveAndSurvival(viewer)
                            || !GameFunctions.isPlayerAliveAndSurvival(targetPlayer)) {
                        return InstinctApi.HighlightResult.pass();
                    }
                    GameWorldComponent gameWorld = GameWorldComponent.KEY.get(viewer.getWorld());
                    if (!gameWorld.canUseKillerFeatures(viewer)) {
                        return InstinctApi.HighlightResult.pass();
                    }
                    var targetRole = gameWorld.getRole(targetPlayer);
                    NeutralRoleCategory category = NeutralRoleCategoryApi.resolveCategory(targetRole);
                    if (targetRole == null || category == null || !category.killerInstinctRevealsRoleColor()) {
                        return InstinctApi.HighlightResult.pass();
                    }
                    return InstinctApi.HighlightResult.color(targetRole.color());
                }
        );
    }
}
