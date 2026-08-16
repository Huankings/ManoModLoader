package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.PlayerLifeStateApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerBlackoutEffectComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerNoteComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.cca.PlayerStaminaComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.events.ModifierRemoved;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SetRoleCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("setRole")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("role", RoleArgumentType.create())
                                .executes(context -> execute(context, TransferMode.RESET))
                                .then(CommandManager.literal("reset")
                                        .executes(context -> execute(context, TransferMode.RESET)))
                                .then(CommandManager.literal("state")
                                        .executes(context -> execute(context, TransferMode.STATE)))
                                .then(CommandManager.literal("soft")
                                        .executes(context -> execute(context, TransferMode.SOFT))))));
    }

    private static int execute(CommandContext<ServerCommandSource> context, TransferMode mode) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        Role newRole = RoleArgumentType.getRole(context, "role");
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.getWorld());
        Role oldRole = gameWorld.getRole(player);

        if (mode == TransferMode.RESET) {
            resetForInGameRoleTransfer(player, oldRole);
        } else if (mode == TransferMode.STATE) {
            stateResetForInGameRoleTransfer(player, oldRole);
        } else {
            softRemoveOldRole(player, oldRole);
        }

        gameWorld.addRole(player, newRole);
        gameWorld.sync();

        /*
         * 直接转成杀手阵营时补上基础金币。
         * 正常开局里扩展杀手是先拿到 Wathe 原版杀手位再被替换，所以金币已经由 Wathe 发过；
         * 调试转职绕过了这一步，需要在这里兜底。
         */
        if (newRole.getFaction() == Faction.KILLER) {
            PlayerShopComponent.KEY.get(player).setBalance(GameConstants.MONEY_START);
        }
        if (newRole == WatheRoles.VIGILANTE) {
            player.giveItemStack(new ItemStack(WatheItems.REVOLVER));
        }
        if (!Harpymodloader.VANNILA_ROLES.contains(newRole)) {
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, newRole);
        }
        sendRoleAnnouncement(player, newRole, gameWorld);

        MutableText roleText = Harpymodloader.getRoleName(newRole)
                .withColor(newRole.color())
                .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(newRole.identifier().toString()))));
        context.getSource().sendMessage(Text.literal("Set ")
                .formatted(Formatting.GRAY)
                .append(player.getDisplayName())
                .append(Text.literal("'s role to ").formatted(Formatting.GRAY))
                .append(roleText)
                .append(Text.literal(mode.feedbackSuffix()).formatted(mode.feedbackColor())));
        return 1;
    }

    private static void resetForInGameRoleTransfer(ServerPlayerEntity player, Role oldRole) {
        /*
         * /setRole reset 是“局内调试转职”的硬清理，而不是 Wathe 正常退场。
         *
         * 之前这里直接调用 GameFunctions#resetPlayer，会同时做三件不适合局内调试的事：
         * 1. 发送 AnnounceEndingPayload，客户端会播放/展示结算结束效果；
         * 2. 清空整个背包，连当前局钥匙和信件也会被删掉；
         * 3. 把玩家传送回地图出生点，容易离开 playArea 后被判定淘汰。
         *
         * 所以这里手动复刻“需要清理的状态”，但明确不发送结算 payload、不传送，
         * 并在清背包时保留 Wathe 的 KEY / LETTER。这样管理员可以在局内连续调试职业，
         * 同时仍能获得接近新一轮职业初始化的干净状态。
         */
        softRemoveOldRole(player, oldRole);
        clearModifiers(player);
        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        clearInventoryExceptWatheRoundBasics(player);
        resetWathePlayerStateForTransfer(player);
    }

    private static void stateResetForInGameRoleTransfer(ServerPlayerEntity player, Role oldRole) {
        /*
         * state 是介于 reset 和 soft 之间的转职模式：
         * - 清掉旧职业物品，并通知扩展清理旧职业的局内组件；
         * - 保留 Wathe 的心情任务、金币、毒药、体力、便签内容等本局进度；
         * - 保留玩家现有词条，但在 ResetPlayerEvent 后重新广播 ModifierAssigned，
         *   让羽毛/矮小这类靠分配事件施加的状态效果不会因为组件重置而“列表还在、效果没了”。
         *
         * 这个模式用于模拟 NoellesRoles 里初学者等“局内变更职业”的轻重置语义。
         */
        softRemoveOldRole(player, oldRole);

        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.getWorld());
        List<Modifier> preservedModifiers = new ArrayList<>(modifiers.getModifiers(player));

        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        clearInventoryExceptWatheRoundBasics(player);

        for (Modifier modifier : preservedModifiers) {
            ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
        }
        modifiers.sync();
    }

    private static void softRemoveOldRole(ServerPlayerEntity player, Role oldRole) {
        if (oldRole != null) {
            /*
             * 旧事件会给扩展一个“身份已经被管理员换走”的收尾机会。
             * 当前 NoellesRoles 主要依赖 ResetPlayerEvent 清理组件，但这里保留事件，
             * 兼容其它扩展或后续职业自己注册更窄的移除逻辑。
             */
            ModdedRoleRemoved.EVENT.invoker().removeModdedRole(player, oldRole);
        }
    }

    private static void clearModifiers(ServerPlayerEntity player) {
        WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(player.getWorld());
        /*
         * reset 模式应当连旧词条也一起清掉，否则玩家可能转成新职业后仍保留恋人、
         * 双重人格、猜测者等局内判定。先复制再广播，避免监听器改动原列表时触发并发修改。
         */
        for (Modifier modifier : new ArrayList<>(modifiers.getModifiers(player))) {
            ModifierRemoved.EVENT.invoker().removeModifier(player, modifier);
        }
        modifiers.getModifiers(player).clear();
        modifiers.sync();
    }

    private static void resetWathePlayerStateForTransfer(ServerPlayerEntity player) {
        /*
         * 这些是 GameFunctions#resetPlayer 中真正和“玩家状态归零”有关的 Wathe 组件。
         * 这里刻意不发送 AnnounceEndingPayload，也不读取地图出生点传送玩家。
         */
        player.dismountVehicle();
        PlayerMoodComponent.KEY.get(player).reset();
        PlayerStaminaComponent.KEY.get(player).reset();
        PlayerShopComponent.KEY.get(player).reset();
        PlayerPoisonComponent.KEY.get(player).reset();
        PlayerPsychoComponent.KEY.get(player).reset();
        PlayerNoteComponent.KEY.get(player).reset();
        PlayerBlackoutEffectComponent.KEY.get(player).clearOwnedEffect();
        PlayerLifeStateApi.clearAliveOverride(player);
        resetTrainVoiceGroupIfAvailable(player);

        player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
        player.wakeUp();
        clearItemCooldowns(player);
    }

    private static void clearItemCooldowns(ServerPlayerEntity player) {
        /*
         * 转职硬重置后旧职业道具已经被清掉，继续保留旧道具冷却只会干扰新职业调试。
         * ItemCooldownManager 的完整表在当前 Yarn 下不是公开字段；Harpy 也不应该为了调试命令
         * 新增一个 mixin/accessor。因此这里清理 Wathe 本体已知道具，扩展职业自己的道具冷却
         * 交给对应扩展在 ResetPlayerEvent 里清理。
         */
        for (Item item : List.of(
                WatheItems.LOCKPICK,
                WatheItems.KNIFE,
                WatheItems.BAT,
                WatheItems.CROWBAR,
                WatheItems.GRENADE,
                WatheItems.FIRECRACKER,
                WatheItems.REVOLVER,
                WatheItems.DERRINGER,
                WatheItems.BODY_BAG,
                WatheItems.BLACKOUT,
                WatheItems.PSYCHO_MODE,
                WatheItems.POISON_VIAL,
                WatheItems.SCORPION,
                WatheItems.OLD_FASHIONED,
                WatheItems.MOJITO,
                WatheItems.MARTINI,
                WatheItems.COSMOPOLITAN,
                WatheItems.CHAMPAGNE,
                WatheItems.NOTE
        )) {
            player.getItemCooldownManager().remove(item);
        }
    }

    private static void resetTrainVoiceGroupIfAvailable(ServerPlayerEntity player) {
        /*
         * Wathe 的 TrainVoicePlugin 实现了 simple voice chat 的接口。
         * Harpy 的 build.gradle 没有 voicechat API，直接 import 会让 Harpy 编译失败；
         * 但运行端如果加载了 Wathe/voicechat，又希望 reset 语义尽量接近 Wathe 原流程。
         * 所以这里用反射做软调用：依赖存在就重置语音分组，不存在就静默跳过。
         */
        try {
            Class<?> pluginClass = Class.forName("dev.doctor4t.wathe.compat.TrainVoicePlugin");
            pluginClass.getMethod("resetPlayer", UUID.class).invoke(null, player.getUuid());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // voicechat 缺失或 Wathe 侧类不可加载时，不影响局内转职本身。
        }
    }

    private static void clearInventoryExceptWatheRoundBasics(ServerPlayerEntity player) {
        /*
         * 当前 Harpy/Wathe 没有“这是职业开局物品”的统一物品标签。
         * 因此局内转职清背包采用和 NoellesRoles 初学者转职相同的保守规则：
         * 只保留 Wathe 本局基础物品 KEY / LETTER，其余物品视作旧职业或旧调试状态残留。
         */
        clearListExceptWatheRoundBasics(player.getInventory().main);
        clearListExceptWatheRoundBasics(player.getInventory().offHand);
        clearListExceptWatheRoundBasics(player.getInventory().armor);
        player.getInventory().markDirty();
    }

    private static void clearListExceptWatheRoundBasics(DefaultedList<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty() || stack.isOf(WatheItems.KEY) || stack.isOf(WatheItems.LETTER)) {
                continue;
            }
            stacks.set(i, ItemStack.EMPTY);
        }
    }

    private static void sendRoleAnnouncement(ServerPlayerEntity player, Role role, GameWorldComponent gameWorld) {
        RoleAnnouncementTexts.RoleAnnouncementText announcement = resolveAnnouncement(role);
        int announcementIndex = RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(announcement);
        if (announcementIndex < 0) {
            announcementIndex = RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(GameRoundEndComponent.getAnnouncementByFaction(role.getFaction()));
        }
        if (announcementIndex < 0) {
            /*
             * 理论上 faction fallback 一定存在；这里再兜一层 BLANK，
             * 避免某个扩展职业加载顺序异常时客户端因为 -1 直接忽略欢迎公告。
             */
            announcementIndex = RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(RoleAnnouncementTexts.BLANK);
        }

        List<ServerPlayerEntity> activePlayers = player.getServerWorld().getPlayers(serverPlayer -> {
            if (gameWorld.getRole(serverPlayer) == null) {
                return false;
            }
            return !gameWorld.isRunning() || GameFunctions.isPlayerAliveAndSurvival(serverPlayer);
        });
        int killerCount = (int) activePlayers.stream()
                .filter(serverPlayer -> {
                    Role playerRole = gameWorld.getRole(serverPlayer);
                    return playerRole != null && playerRole.getFaction() == Faction.KILLER;
                })
                .count();
        int targetCount = Math.max(0, activePlayers.size() - killerCount);

        ServerPlayNetworking.send(player, new AnnounceWelcomePayload(announcementIndex, killerCount, targetCount));
    }

    private static RoleAnnouncementTexts.RoleAnnouncementText resolveAnnouncement(Role role) {
        if (!Harpymodloader.VANNILA_ROLES.contains(role)) {
            RoleAnnouncementTexts.RoleAnnouncementText announcement = Harpymodloader.autogeneratedAnnouncements.get(role);
            if (announcement != null) {
                return announcement;
            }
        }
        return GameRoundEndComponent.getAnnouncementByFaction(role.getFaction());
    }

    private enum TransferMode {
        RESET(" with reset.", Formatting.GREEN),
        STATE(" with state reset.", Formatting.AQUA),
        SOFT(" softly.", Formatting.YELLOW);

        private final String feedbackSuffix;
        private final Formatting feedbackColor;

        TransferMode(String feedbackSuffix, Formatting feedbackColor) {
            this.feedbackSuffix = feedbackSuffix;
            this.feedbackColor = feedbackColor;
        }

        private String feedbackSuffix() {
            return this.feedbackSuffix;
        }

        private Formatting feedbackColor() {
            return this.feedbackColor;
        }
    }
}
