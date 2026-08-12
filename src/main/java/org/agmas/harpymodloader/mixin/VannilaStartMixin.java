package org.agmas.harpymodloader.mixin;

import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.MapEffect;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.AutoStartComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.command.StartCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.world.World;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StartCommand.class)
public class VannilaStartMixin {

    @Inject(method = "execute", at = @At("HEAD"))
    private static void a(ServerCommandSource source, GameMode gameMode, MapEffect mapEffect, int minutes, CallbackInfoReturnable<Integer> cir) {
        if (gameMode.equals(WatheGameModes.MURDER)) Harpymodloader.wantsToStartVannila = true;
     }

    @Inject(method = "execute", at = @At("RETURN"))
    private static void clearVanillaStartIntent(ServerCommandSource source, GameMode gameMode, MapEffect mapEffect, int minutes, CallbackInfoReturnable<Integer> cir) {
        /*
         * execute 可能因为对局已在运行、人数不足等原因提前结束，根本不会进入
         * GameFunctions.initializeGame。此时如果不在命令返回点清理标记，
         * 下一次自动开局会错误地继续使用原版 Murder。
         */
        Harpymodloader.wantsToStartVannila = false;
    }
}
