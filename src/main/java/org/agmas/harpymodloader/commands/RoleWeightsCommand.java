package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.ScoreboardRoleSelectorComponent;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RoleWeightsCommand {
    private static final DynamicCommandExceptionType INVALID_FACTION = new DynamicCommandExceptionType(input -> Text.literal("Unknown faction: " + input));
    private static final DynamicCommandExceptionType INVALID_UUID = new DynamicCommandExceptionType(input -> Text.literal("Invalid UUID: " + input));

    private enum ListScope {
        ALL, ONLINE, STORED
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        /*
         * 权重调试指令集中放在 Harpy 侧，是因为 Harpy 能解析 Wathe 原版职业和所有扩展职业。
         * 真正的数据仍保存在 Wathe 的 ScoreboardRoleSelectorComponent 里，保证原版 Murder 和
         * Harpy modded murder 共用同一套公平历史。
         */
        dispatcher.register(CommandManager.literal("roleWeights")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> list(context, ListScope.ALL))
                .then(CommandManager.literal("list")
                        .executes(context -> list(context, ListScope.ALL))
                        .then(CommandManager.literal("all").executes(context -> list(context, ListScope.ALL)))
                        .then(CommandManager.literal("online").executes(context -> list(context, ListScope.ONLINE)))
                        .then(CommandManager.literal("stored").executes(context -> list(context, ListScope.STORED))))
                .then(CommandManager.literal("enabled")
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(RoleWeightsCommand::setEnabled)))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.literal("all").executes(RoleWeightsCommand::resetAll))
                        .then(CommandManager.literal("online").executes(RoleWeightsCommand::resetOnline))
                        .then(CommandManager.literal("storedOffline").executes(RoleWeightsCommand::resetStoredOffline))
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(RoleWeightsCommand::resetPlayers)))
                        .then(CommandManager.literal("uuid")
                                .then(CommandManager.argument("uuid", StringArgumentType.word())
                                        .executes(RoleWeightsCommand::resetUuid))))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.literal("faction")
                                                .then(CommandManager.argument("faction", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("civilian", "vigilante", "killer", "neutral"), builder))
                                                        .then(CommandManager.argument("weight", DoubleArgumentType.doubleArg(0.0D, 10_000.0D))
                                                                .executes(RoleWeightsCommand::setPlayerFactionWeight))))
                                        .then(CommandManager.literal("role")
                                                .then(CommandManager.argument("role", RoleArgumentType.create())
                                                        .then(CommandManager.argument("weight", DoubleArgumentType.doubleArg(0.0D, 10_000.0D))
                                                                .executes(RoleWeightsCommand::setPlayerRoleWeight)))))))
                .then(CommandManager.literal("clearOverride")
                        .then(CommandManager.literal("player")
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(RoleWeightsCommand::clearPlayerOverrides))))
                .then(CommandManager.literal("preview")
                        .then(CommandManager.literal("faction")
                                .then(CommandManager.argument("faction", StringArgumentType.word())
                                        .suggests((context, builder) -> CommandSource.suggestMatching(List.of("civilian", "vigilante", "killer", "neutral"), builder))
                                        .executes(RoleWeightsCommand::previewFaction)))
                        .then(CommandManager.literal("role")
                                .then(CommandManager.argument("role", RoleArgumentType.create())
                                        .executes(RoleWeightsCommand::previewRole)))));
    }

    private static int list(CommandContext<ServerCommandSource> context, ListScope scope) {
        ServerCommandSource source = context.getSource();
        ScoreboardRoleSelectorComponent selector = getSelector(source);
        Map<UUID, ServerPlayerEntity> onlinePlayers = new LinkedHashMap<>();
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            onlinePlayers.put(player.getUuid(), player);
        }

        ArrayList<UUID> uuids = new ArrayList<>();
        /*
         * online 展示当前服务器在线玩家，stored 展示已有历史记录，
         * all 则把两者合并。这样管理员既能看正在参与测试的人，
         * 也能查到已经离线但仍留有权重账本的玩家。
         */
        if (scope != ListScope.STORED) {
            uuids.addAll(onlinePlayers.keySet());
        }
        if (scope != ListScope.ONLINE) {
            for (UUID uuid : selector.getKnownWeightPlayers()) {
                if (!uuids.contains(uuid)) {
                    uuids.add(uuid);
                }
            }
        }

        uuids.sort(Comparator.comparing(uuid -> getDisplayName(uuid, onlinePlayers.get(uuid), selector).getString(), String.CASE_INSENSITIVE_ORDER));

        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(source.getWorld());
        MutableText message = Text.literal("Role Weights ").formatted(Formatting.GRAY)
                .append(Text.literal("(" + scope.name().toLowerCase(Locale.ROOT) + ", " + (gameWorld.areWeightsEnabled() ? "enabled" : "disabled") + ")")
                        .formatted(gameWorld.areWeightsEnabled() ? Formatting.GREEN : Formatting.RED));

        if (uuids.isEmpty()) {
            message.append("\n").append(Text.literal("No stored role weight records.").formatted(Formatting.DARK_GRAY));
        }

        for (UUID uuid : uuids) {
            ServerPlayerEntity player = onlinePlayers.get(uuid);
            ScoreboardRoleSelectorComponent.RoleWeightRecord record = selector.getRoleWeightRecord(uuid);
            message.append("\n").append(formatPlayerHeader(uuid, player, record));
            appendFactionCounts(message, record);
            appendRoleCounts(message, record);
            appendOverrides(message, record);
        }

        /*
         * 和 /listRoles 一样直接发消息，不依赖 sendCommandFeedback。
         * 权重报告通常比较长，管理员查询时应该总能看到完整文本。
         */
        source.sendMessage(message);
        return uuids.size();
    }

    private static int setEnabled(CommandContext<ServerCommandSource> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        GameWorldComponent.KEY.get(context.getSource().getWorld()).setWeightsEnabled(enabled);
        context.getSource().sendMessage(Text.literal("Role weights are now " + (enabled ? "enabled" : "disabled") + ".")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));
        return 1;
    }

    private static int resetAll(CommandContext<ServerCommandSource> context) {
        getSelector(context.getSource()).resetAllWeights();
        context.getSource().sendMessage(Text.literal("All stored role weights have been reset.").formatted(Formatting.GREEN));
        return 1;
    }

    private static int resetOnline(CommandContext<ServerCommandSource> context) {
        List<ServerPlayerEntity> players = context.getSource().getWorld().getPlayers();
        getSelector(context.getSource()).resetWeights(players);
        context.getSource().sendMessage(Text.literal("Reset role weights for " + players.size() + " online player(s) in this world.").formatted(Formatting.GREEN));
        return players.size();
    }

    private static int resetStoredOffline(CommandContext<ServerCommandSource> context) {
        int removed = getSelector(context.getSource()).resetStoredOfflineWeights(context.getSource().getServer().getPlayerManager().getPlayerList());
        context.getSource().sendMessage(Text.literal("Reset " + removed + " stored offline role weight record(s).").formatted(Formatting.GREEN));
        return removed;
    }

    private static int resetPlayers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        List<ServerPlayerEntity> players = new ArrayList<>(EntityArgumentType.getPlayers(context, "players"));
        getSelector(context.getSource()).resetWeights(players);
        context.getSource().sendMessage(Text.literal("Reset role weights for " + players.size() + " selected player(s).").formatted(Formatting.GREEN));
        return players.size();
    }

    private static int resetUuid(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID uuid = parseUuid(StringArgumentType.getString(context, "uuid"));
        getSelector(context.getSource()).resetWeights(uuid);
        context.getSource().sendMessage(Text.literal("Reset role weights for " + uuid + ".").formatted(Formatting.GREEN));
        return 1;
    }

    private static int setPlayerFactionWeight(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        Faction faction = parseFaction(StringArgumentType.getString(context, "faction"));
        double weight = DoubleArgumentType.getDouble(context, "weight");
        /*
         * 手动设置的是“调试覆盖权重”，不是历史次数。
         * 开启权重时，对应阵营会直接使用这个数值，方便把多个玩家设成相同权重后验证概率是否一致。
         * reset player/all 会把这些覆盖值一起清掉。
         */
        getSelector(context.getSource()).setFactionWeightOverride(player, faction, weight);
        context.getSource().sendMessage(Text.literal("Set ")
                .formatted(Formatting.GRAY)
                .append(player.getDisplayName())
                .append(Text.literal("'s ").formatted(Formatting.GRAY))
                .append(formatFaction(faction))
                .append(Text.literal(" debug weight to %.4f.".formatted(weight)).formatted(Formatting.GRAY)));
        return 1;
    }

    private static int setPlayerRoleWeight(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        Role role = RoleArgumentType.getRole(context, "role");
        double weight = DoubleArgumentType.getDouble(context, "weight");
        /*
         * 具体职业覆盖优先于阵营覆盖。
         * 这能单独测试某个扩展职业的替换概率，而不影响同阵营其它职业。
         */
        getSelector(context.getSource()).setRoleWeightOverride(player, role, weight);
        context.getSource().sendMessage(Text.literal("Set ")
                .formatted(Formatting.GRAY)
                .append(player.getDisplayName())
                .append(Text.literal("'s ").formatted(Formatting.GRAY))
                .append(formatRole(role))
                .append(Text.literal(" debug weight to %.4f.".formatted(weight)).formatted(Formatting.GRAY)));
        return 1;
    }

    private static int clearPlayerOverrides(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        List<ServerPlayerEntity> players = new ArrayList<>(EntityArgumentType.getPlayers(context, "players"));
        ScoreboardRoleSelectorComponent selector = getSelector(context.getSource());
        for (ServerPlayerEntity player : players) {
            selector.clearWeightOverrides(player);
        }
        context.getSource().sendMessage(Text.literal("Cleared debug weight overrides for " + players.size() + " player(s).").formatted(Formatting.GREEN));
        return players.size();
    }

    private static int previewFaction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Faction faction = parseFaction(StringArgumentType.getString(context, "faction"));
        return preview(context.getSource(), faction, null, true, false, "faction " + faction.name().toLowerCase(Locale.ROOT));
    }

    private static int previewRole(CommandContext<ServerCommandSource> context) {
        Role role = RoleArgumentType.getRole(context, "role");
        return preview(context.getSource(), role.getFaction(), role, true, true, "role " + role.identifier());
    }

    private static int preview(ServerCommandSource source,
                               Faction faction,
                               Role role,
                               boolean includeFactionHistory,
                               boolean includeRoleHistory,
                               String title) {
        List<ServerPlayerEntity> players = source.getWorld().getPlayers();
        ScoreboardRoleSelectorComponent selector = getSelector(source);
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(source.getWorld());
        LinkedHashMap<ServerPlayerEntity, Double> weights = selector.getAssignmentWeights(gameWorld, players, faction, role, includeFactionHistory, includeRoleHistory);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        /*
         * preview 只读当前公式计算出的本次概率，不消耗、不记录、不改变职业。
         * 它用于开局前快速检查“谁更容易被抽到杀手/中立/某个扩展职业”。
         */
        MutableText message = Text.literal("Role Weight Preview: ").formatted(Formatting.GRAY)
                .append(Text.literal(title).formatted(Formatting.YELLOW));
        for (Map.Entry<ServerPlayerEntity, Double> entry : weights.entrySet()) {
            double percentage = total <= 0.0D ? 0.0D : entry.getValue() / total * 100.0D;
            message.append("\n")
                    .append(entry.getKey().getDisplayName())
                    .append(Text.literal(" -> ").formatted(Formatting.GRAY))
                    .append(Text.literal("%.4f".formatted(entry.getValue())).withColor(0x808080))
                    .append(Text.literal(" / ").formatted(Formatting.GRAY))
                    .append(Text.literal("%.2f%%".formatted(percentage)).withColor(0x808080));
        }

        source.sendMessage(message);
        return weights.size();
    }

    private static ScoreboardRoleSelectorComponent getSelector(ServerCommandSource source) {
        return ScoreboardRoleSelectorComponent.KEY.get(source.getServer().getScoreboard());
    }

    private static Faction parseFaction(String raw) throws CommandSyntaxException {
        try {
            return Faction.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw INVALID_FACTION.create(raw);
        }
    }

    private static UUID parseUuid(String raw) throws CommandSyntaxException {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw INVALID_UUID.create(raw);
        }
    }

    private static MutableText formatPlayerHeader(UUID uuid, ServerPlayerEntity player, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        MutableText name = getDisplayName(uuid, player, null).copy();
        if (record != null && record.getLastKnownName() != null && !record.getLastKnownName().isBlank()) {
            name = Text.literal(record.getLastKnownName()).formatted(Formatting.YELLOW);
        }
        if (player != null) {
            name = player.getDisplayName().copy().formatted(Formatting.YELLOW);
        }
        return name.styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(uuid.toString()))));
    }

    private static MutableText getDisplayName(UUID uuid, ServerPlayerEntity player, ScoreboardRoleSelectorComponent selector) {
        if (player != null) {
            return player.getDisplayName().copy();
        }
        if (selector != null) {
            ScoreboardRoleSelectorComponent.RoleWeightRecord record = selector.getRoleWeightRecord(uuid);
            if (record != null && record.getLastKnownName() != null && !record.getLastKnownName().isBlank()) {
                return Text.literal(record.getLastKnownName());
            }
        }
        return Text.literal(uuid.toString());
    }

    private static void appendFactionCounts(MutableText message, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        message.append(Text.literal("\n  Factions: ").formatted(Formatting.GRAY));
        for (Faction faction : Faction.values()) {
            int count = record == null ? 0 : record.getFactionRounds(faction);
            message.append(formatFaction(faction))
                    .append(Text.literal("=" + count + " ").withColor(0x808080));
        }
        if (record != null && record.getLastFaction() != null) {
            message.append(Text.literal("last=").formatted(Formatting.DARK_GRAY))
                    .append(formatFaction(record.getLastFaction()))
                    .append(Text.literal("x" + record.getConsecutiveFactionRounds()).withColor(0x808080));
        }
    }

    private static void appendRoleCounts(MutableText message, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        message.append(Text.literal("\n  Roles: ").formatted(Formatting.GRAY));
        if (record == null || record.getRoleRoundsView().isEmpty()) {
            message.append(Text.literal("none").formatted(Formatting.DARK_GRAY));
            return;
        }

        List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(record.getRoleRoundsView().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (Map.Entry<Identifier, Integer> entry : entries) {
            Role role = WatheRoles.getRole(entry.getKey());
            if (role != null) {
                message.append(formatRole(role));
            } else {
                message.append(Text.literal(entry.getKey().toString()).formatted(Formatting.YELLOW));
            }
            message.append(Text.literal("=" + entry.getValue() + " ").withColor(0x808080));
        }
    }

    private static void appendOverrides(MutableText message, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        if (record == null || (record.getFactionWeightOverridesView().isEmpty() && record.getRoleWeightOverridesView().isEmpty())) {
            return;
        }

        message.append(Text.literal("\n  Debug overrides: ").formatted(Formatting.GRAY));
        for (Map.Entry<Faction, Double> entry : record.getFactionWeightOverridesView().entrySet()) {
            message.append(formatFaction(entry.getKey()))
                    .append(Text.literal("=%.4f ".formatted(entry.getValue())).withColor(0x808080));
        }
        for (Map.Entry<Identifier, Double> entry : record.getRoleWeightOverridesView().entrySet()) {
            Role role = WatheRoles.getRole(entry.getKey());
            if (role != null) {
                message.append(formatRole(role));
            } else {
                message.append(Text.literal(entry.getKey().toString()).formatted(Formatting.YELLOW));
            }
            message.append(Text.literal("=%.4f ".formatted(entry.getValue())).withColor(0x808080));
        }
    }

    private static MutableText formatFaction(Faction faction) {
        return Text.literal(faction.name().toLowerCase(Locale.ROOT)).withColor(faction.displayColor());
    }

    private static MutableText formatRole(Role role) {
        return Harpymodloader.getRoleName(role)
                .withColor(role.color())
                .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(role.identifier().toString()))));
    }
}
