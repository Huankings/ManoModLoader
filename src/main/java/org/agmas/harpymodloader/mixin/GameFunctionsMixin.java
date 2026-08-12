package org.agmas.harpymodloader.mixin;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class GameFunctionsMixin {
    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void a(ServerWorld serverWorld, CallbackInfo ci) {
        /*
         * 主要的 Murder -> Harpy modded 解析已经提前移动到
         * Harpymodloader#registerStartGameModeResolver，使人数判断和大厅 HUD 都能读到 Harpy 自己的门槛。
         * initializeGame 阶段只清理“显式开原版 Murder”的一次性标记；
         * 不能再二次改 gameMode，否则 /wathe:start murder ... 会在淡入淡出结束后被误切回 modded。
         */
        Harpymodloader.wantsToStartVannila = false;
    }
}
