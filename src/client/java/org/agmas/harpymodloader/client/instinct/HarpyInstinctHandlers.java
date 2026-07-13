package org.agmas.harpymodloader.client.instinct;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.instinct.InstinctApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;

public final class HarpyInstinctHandlers {
    private HarpyInstinctHandlers() {
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
             * HarpyModLoader 的旧 mixin 只在旁观/创造视角下改写玩家描边：
             * 不显示阵营推断色，而是直接显示目标登记到 GameWorldComponent 里的真实职业色。
             * 这里保持同样语义，但通过 Wathe 的 highlight API 接入，避免继续抢 WatheClient 的 HEAD 注入。
             */
            GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(viewer.getWorld());
            Role role = gameWorldComponent.getRole(targetPlayer);
            return InstinctApi.HighlightResult.color(role == null ? WatheRoles.CIVILIAN.color() : role.color());
        });
    }
}
