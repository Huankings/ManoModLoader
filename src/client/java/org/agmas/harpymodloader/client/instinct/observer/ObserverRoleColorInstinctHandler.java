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
    /*
     * 非存活观察者的职业色透视是“全局观察视角”的规则。
     * 它必须压过所有扩展职业/词条自己的本能颜色，否则像 Jester、双重人格这类
     * 死后仍保留职业或运行态数据的玩家，会先返回自己的颜色并让 InstinctApi 短路。
     */
    private static final int PRIORITY_OBSERVER_ROLE_COLOR = Integer.MAX_VALUE;

    private ObserverRoleColorInstinctHandler() {
    }

    public static void register() {
        InstinctApi.registerAvailability(Identifier.of(Harpymodloader.MOD_ID, "observer_role_color_availability"), PRIORITY_OBSERVER_ROLE_COLOR, viewer -> {
            /*
             * 观察者透视只看“非存活旁观/创造 + 本能键输入”。
             * 这里不检查职业身份，确保死亡后的任意职业都会切换到统一的观察者本能。
             */
            if (GameFunctions.isPlayerSpectatingOrCreative(viewer) && WatheClient.isInstinctInputActive()) {
                return InstinctApi.AvailabilityResult.ENABLE;
            }
            return InstinctApi.AvailabilityResult.PASS;
        });

        InstinctApi.registerHighlight(Identifier.of(Harpymodloader.MOD_ID, "observer_role_color"), PRIORITY_OBSERVER_ROLE_COLOR, (viewer, target) -> {
            if (!(target instanceof PlayerEntity targetPlayer)) {
                return InstinctApi.HighlightResult.pass();
            }
            if (!WatheClient.isInstinctEnabled()) {
                return InstinctApi.HighlightResult.pass();
            }
            if (!GameFunctions.isPlayerSpectatingOrCreative(viewer) || !GameFunctions.isPlayerAliveAndSurvival(targetPlayer)) {
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
