package org.agmas.harpymodloader.client;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.client.mood.MoodHudApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Identifier;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import java.util.ArrayList;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.client.instinct.HarpyInstinctHandlers;
import org.agmas.harpymodloader.modifiers.Modifier;

public class HarpymodloaderClient implements ClientModInitializer {

    public static float rainbowRoleTime = 0;
    public static Role hudRole = null;
    public static ArrayList<Modifier> modifiers = null;

    @Override
    public void onInitializeClient() {
        HarpyInstinctHandlers.register();
        /*
         * Harpy 自己的 modded game mode 本质上仍使用 Wathe 的心情 HUD。
         * 以前这里靠 MoodRenderer mixin 把 MODDED_GAMEMODE 伪装成 MURDER；
         * 现在改成正式注册入口，后续其它 game mode 也可以按同样方式接入。
         */
        MoodHudApi.registerVisibleGameModePredicate(
                Identifier.of(Harpymodloader.MOD_ID, "mood/modded_game_mode"),
                MoodHudApi.DEFAULT_PRIORITY,
                gameMode -> gameMode == Harpymodloader.MODDED_GAMEMODE
        );

        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, minecraftClient) -> {
            Harpymodloader.refreshRoles();
        });
        ClientTickEvents.END_CLIENT_TICK.register((t) -> {
            rainbowRoleTime += 1;
        });
    }

}
