package org.agmas.harpymodloader.client.instinct.observer;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.instinct.InstinctApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;

public final class ObserverRoleColorInstinctHandler {
    private ObserverRoleColorInstinctHandler() {
    }

    public static void register() {
        InstinctApi.registerHighlight(Identifier.of(Harpymodloader.MOD_ID, "observer_role_color"), InstinctApi.DEFAULT_PRIORITY, (viewer, target) -> {
            if (!(target instanceof PlayerEntity targetPlayer)) {
                return InstinctApi.HighlightResult.pass();
            }
            if (!WatheClient.isInstinctEnabled()) {
                return InstinctApi.HighlightResult.pass();
            }
            if (!GameFunctions.isPlayerSpectatingOrCreative(viewer) || GameFunctions.isPlayerSpectatingOrCreative(targetPlayer)) {
                return InstinctApi.HighlightResult.pass();
            }

            /*
             * HarpyModLoader 的旁观/创造视角不显示 Wathe 默认阵营推断色，
             * 而是直接显示目标真实职业色。没有职业数据时按平民色兜底。
             */
            GameWorldComponent gameWorld = GameWorldComponent.KEY.get(viewer.getWorld());
            Role role = gameWorld.getRole(targetPlayer);
            return InstinctApi.HighlightResult.color(role == null ? WatheRoles.CIVILIAN.color() : role.color());
        });
    }
}
