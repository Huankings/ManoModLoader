package org.agmas.harpymodloader.mixin;

import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(GameFunctions.class)
public class ModifierAndRoleResetMixin {
    @Inject(method = "resetPlayer", at = @At("HEAD"))
    private static void a(ServerPlayerEntity player, CallbackInfo ci) {
        GameWorldComponent gameComponent = (GameWorldComponent)GameWorldComponent.KEY.get(player.getWorld());
        if (gameComponent.getRole(player) != null) {
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, gameComponent.getRole(player));
        }
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(player.getWorld());
        /*
         * resetPlayer 是“玩家状态硬清理”的入口。旧逻辑只广播 ModifierRemoved，
         * 但没有真的把 WorldModifierComponent 里的词条删掉，导致 /setRole reset 之后
         * 玩家仍可能保留恋人、双重人格、猜测者等旧词条判定。
         *
         * 这里先复制一份列表再广播事件，避免监听器在响应过程中改动原列表引发并发修改；
         * 广播结束后再清空玩家当前词条，保证调试转职和回合重置看到的都是干净状态。
         */
        for (Modifier modifier : new ArrayList<>(worldModifierComponent.getModifiers(player))) {
            ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
        }
        worldModifierComponent.getModifiers(player).clear();
        worldModifierComponent.sync();
        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
    }

    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void b(ServerWorld serverWorld, CallbackInfo ci) {
        for (ServerPlayerEntity player : serverWorld.getPlayers()) {
            ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        }
    }
}
