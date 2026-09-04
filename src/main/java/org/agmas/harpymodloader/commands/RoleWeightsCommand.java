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
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RoleWeightsCommand {
    private static final String LANG_PREFIX = "commands.harpymodloader.roleweights.";
    private static final DynamicCommandExceptionType INVALID_FACTION = new DynamicCommandExceptionType(input -> tr("error.invalid_faction", input));
    private static final DynamicCommandExceptionType INVALID_UUID = new DynamicCommandExceptionType(input -> tr("error.invalid_uuid", input));

    private static MutableText tr(String key, Object... args) {
        return Text.translatable(LANG_PREFIX + key, args);
    }

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
        MutableText message = tr("list.header", tr("scope." + scope.name().toLowerCase(Locale.ROOT)),
                tr(gameWorld.areWeightsEnabled() ? "status.enabled" : "status.disabled"))
                .formatted(Formatting.GRAY);

        if (uuids.isEmpty()) {
            message.append("\n").append(tr("list.empty").formatted(Formatting.DARK_GRAY));
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
        context.getSource().sendMessage(tr(enabled ? "set_enabled.success" : "set_disabled.success")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED));
        return 1;
    }

    private static int resetAll(CommandContext<ServerCommandSource> context) {
        getSelector(context.getSource()).resetAllWeights();
        context.getSource().sendMessage(tr("reset.all").formatted(Formatting.GREEN));
        return 1;
    }

    private static int resetOnline(CommandContext<ServerCommandSource> context) {
        List<ServerPlayerEntity> players = context.getSource().getWorld().getPlayers();
        getSelector(context.getSource()).resetWeights(players);
        context.getSource().sendMessage(tr("reset.online", players.size()).formatted(Formatting.GREEN));
        return players.size();
    }

    private static int resetStoredOffline(CommandContext<ServerCommandSource> context) {
        int removed = getSelector(context.getSource()).resetStoredOfflineWeights(context.getSource().getServer().getPlayerManager().getPlayerList());
        context.getSource().sendMessage(tr("reset.stored_offline", removed).formatted(Formatting.GREEN));
        return removed;
    }

    private static int resetPlayers(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        List<ServerPlayerEntity> players = new ArrayList<>(EntityArgumentType.getPlayers(context, "players"));
        getSelector(context.getSource()).resetWeights(players);
        context.getSource().sendMessage(tr("reset.players", players.size()).formatted(Formatting.GREEN));
        return players.size();
    }

    private static int resetUuid(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        UUID uuid = parseUuid(StringArgumentType.getString(context, "uuid"));
        getSelector(context.getSource()).resetWeights(uuid);
        context.getSource().sendMessage(tr("reset.uuid", uuid).formatted(Formatting.GREEN));
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
        context.getSource().sendMessage(tr("set_faction.success", player.getDisplayName(), formatFaction(faction), "%.4f".formatted(weight))
                .formatted(Formatting.GRAY));
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
        context.getSource().sendMessage(tr("set_role.success", player.getDisplayName(), formatRole(role), "%.4f".formatted(weight))
                .formatted(Formatting.GRAY));
        return 1;
    }

    private static int clearPlayerOverrides(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        List<ServerPlayerEntity> players = new ArrayList<>(EntityArgumentType.getPlayers(context, "players"));
        ScoreboardRoleSelectorComponent selector = getSelector(context.getSource());
        for (ServerPlayerEntity player : players) {
            selector.clearWeightOverrides(player);
        }
        context.getSource().sendMessage(tr("clear_override.success", players.size()).formatted(Formatting.GREEN));
        return players.size();
    }

    private static int previewFaction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        Faction faction = parseFaction(StringArgumentType.getString(context, "faction"));
        return preview(context.getSource(), faction, null, true, false,
                tr("preview.target.faction", formatFaction(faction)));
    }

    private static int previewRole(CommandContext<ServerCommandSource> context) {
        Role role = RoleArgumentType.getRole(context, "role");
        return preview(context.getSource(), role.getFaction(), role, true, true,
                tr("preview.target.role", formatRole(role)));
    }

    private static int preview(ServerCommandSource source,
                               Faction faction,
                               Role role,
                               boolean includeFactionHistory,
                               boolean includeRoleHistory,
                               MutableText title) {
        List<ServerPlayerEntity> players = source.getServer().getPlayerManager().getPlayerList();
        ScoreboardRoleSelectorComponent selector = getSelector(source);
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(source.getWorld());
        int globalSlots = role == null
                ? selector.estimateFactionSlots(gameWorld, faction, players.size())
                : estimateRoleSlots(selector, gameWorld, role, players.size());
        List<ServerPlayerEntity> conditionalCandidates = getConditionalCandidates(gameWorld, faction, role, players);
        int conditionalSlots = Math.min(globalSlots, conditionalCandidates.size());
        LinkedHashMap<ServerPlayerEntity, Double> globalWeights = selector.getAssignmentWeights(
                gameWorld, players, faction, role, globalSlots, includeFactionHistory, includeRoleHistory);
        LinkedHashMap<ServerPlayerEntity, Double> conditionalWeights = selector.getAssignmentWeights(
                gameWorld, conditionalCandidates, faction, role, conditionalSlots, includeFactionHistory, includeRoleHistory);
        double globalTotal = globalWeights.values().stream().mapToDouble(Double::doubleValue).sum();
        double conditionalTotal = conditionalWeights.values().stream().mapToDouble(Double::doubleValue).sum();

        /*
         * preview 只读当前公式计算出的本次概率，不消耗、不记录、不改变职业。
         * 它用于开局前快速检查“谁更容易被抽到杀手/中立/某个扩展职业”。
         */
        MutableText message = tr("preview.header", title)
                .formatted(Formatting.GRAY)
                .append(tr("preview.global_pool", players.size(), globalSlots).withColor(0x808080))
                .append(tr("preview.conditional_pool", conditionalCandidates.size(), conditionalSlots).withColor(0x808080));
        for (Map.Entry<ServerPlayerEntity, Double> entry : globalWeights.entrySet()) {
            double globalPercentage = globalTotal <= 0.0D ? 0.0D : entry.getValue() / globalTotal * 100.0D;
            Double conditionalWeight = conditionalWeights.get(entry.getKey());
            MutableText conditionalPercentage = conditionalWeight == null || conditionalTotal <= 0.0D
                    ? tr("value.not_available")
                    : Text.literal("%.2f%%".formatted(conditionalWeight / conditionalTotal * 100.0D));
            ScoreboardRoleSelectorComponent.RoleWeightRecord record = selector.getRoleWeightRecord(entry.getKey().getUuid());
            message.append(tr("preview.player", entry.getKey().getDisplayName(), "%.4f".formatted(entry.getValue()),
                    "%.2f%%".formatted(globalPercentage), conditionalPercentage));
            if (record != null) {
                int actual = role == null ? record.getFactionRounds(faction) : record.getRoleRounds(role.identifier());
                int eligible = role == null ? record.getFactionEligibilityRounds(faction) : record.getRoleEligibilityRounds(role.identifier());
                message.append(tr("preview.counts", actual, eligible).withColor(0x808080));
            }
        }

        source.sendMessage(message);
        return globalWeights.size();
    }

    /**
     * 生成 preview 所需的当前候选池。
     * 大厅/非进行中状态没有正在执行的分配阶段，因此保留所有在线玩家作为可参加者；
     * 对局进行中则按当前已写入的最终职业做一层可读过滤，帮助管理员解释“为什么有人不在条件池”。
     */
    private static List<ServerPlayerEntity> getConditionalCandidates(GameWorldComponent gameWorld,
                                                                       Faction faction,
                                                                       Role role,
                                                                       List<ServerPlayerEntity> players) {
        if (gameWorld.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return new ArrayList<>(players);
        }
        ArrayList<ServerPlayerEntity> result = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            Role currentRole = gameWorld.getRole(player);
            if (currentRole == null || currentRole.getFaction() != faction) {
                continue;
            }
            if (role == null || currentRole == role) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * 估算某个扩展职业在全局开局池中占用的槽位。
     * Harpy 会按剩余候选人数/剩余职业类型动态分配，因此这里使用启用职业数作为
     * 可解释的平均值；ROLE_MAX 仍作为硬上限。它只用于 preview，不会改变实际分配。
     */
    private static int estimateRoleSlots(ScoreboardRoleSelectorComponent selector,
                                         GameWorldComponent gameWorld,
                                         Role role,
                                         int playerCount) {
        int factionSlots = selector.estimateFactionSlots(gameWorld, role.getFaction(), playerCount);
        if (Harpymodloader.VANNILA_ROLES.contains(role) || Harpymodloader.SPECIAL_ROLES.contains(role)) {
            /* 原版基础职业不是“扩展职业替换类型”，不应再被扩展职业数量均分。 */
            return factionSlots;
        }
        if (factionSlots <= 0) {
            return 0;
        }
        int enabledRoleCount = 0;
        for (Role candidate : WatheRoles.ROLES) {
            if (Harpymodloader.VANNILA_ROLES.contains(candidate)
                    || Harpymodloader.SPECIAL_ROLES.contains(candidate)
                    || !Harpymodloader.isFaction(candidate, role.getFaction())
                    || HarpyModLoaderConfig.HANDLER.instance().disabled.contains(candidate.identifier().toString())) {
                continue;
            }
            enabledRoleCount++;
        }
        int slots = Math.max(1, (int) Math.ceil((double) Math.max(0, factionSlots) / Math.max(1, enabledRoleCount)));
        if (Harpymodloader.ROLE_MAX.containsKey(role.identifier())) {
            slots = Math.min(slots, Math.max(0, Harpymodloader.ROLE_MAX.get(role.identifier())));
        }
        return slots;
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
        message.append(tr("list.factions").formatted(Formatting.GRAY));
        for (Faction faction : Faction.values()) {
            int count = record == null ? 0 : record.getFactionRounds(faction);
            int eligible = record == null ? 0 : record.getFactionEligibilityRounds(faction);
            message.append(tr("list.faction_entry", formatFaction(faction), count, eligible).withColor(0x808080));
        }
        if (record != null && record.getLastFaction() != null) {
            message.append(tr("list.last", formatFaction(record.getLastFaction()), record.getConsecutiveFactionRounds())
                    .formatted(Formatting.DARK_GRAY));
        }
    }

    private static void appendRoleCounts(MutableText message, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        message.append(tr("list.roles").formatted(Formatting.GRAY));
        if (record == null || record.getRoleRoundsView().isEmpty()) {
            message.append(tr("value.none").formatted(Formatting.DARK_GRAY));
            return;
        }

        List<Map.Entry<Identifier, Integer>> entries = new ArrayList<>(record.getRoleRoundsView().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        for (Map.Entry<Identifier, Integer> entry : entries) {
            Role role = WatheRoles.getRole(entry.getKey());
            MutableText roleText = role != null
                    ? formatRole(role)
                    : Text.literal(entry.getKey().toString()).formatted(Formatting.YELLOW);
            int eligible = record.getRoleEligibilityRounds(entry.getKey());
            message.append(tr("list.role_entry", roleText, entry.getValue(), eligible).withColor(0x808080));
        }
    }

    private static void appendOverrides(MutableText message, ScoreboardRoleSelectorComponent.RoleWeightRecord record) {
        if (record == null || (record.getFactionWeightOverridesView().isEmpty() && record.getRoleWeightOverridesView().isEmpty())) {
            return;
        }

        message.append(tr("list.debug_overrides").formatted(Formatting.GRAY));
        for (Map.Entry<Faction, Double> entry : record.getFactionWeightOverridesView().entrySet()) {
            message.append(tr("list.faction_override", formatFaction(entry.getKey()), "%.4f".formatted(entry.getValue()))
                    .withColor(0x808080));
        }
        for (Map.Entry<Identifier, Double> entry : record.getRoleWeightOverridesView().entrySet()) {
            Role role = WatheRoles.getRole(entry.getKey());
            MutableText roleText = role != null
                    ? formatRole(role)
                    : Text.literal(entry.getKey().toString()).formatted(Formatting.YELLOW);
            message.append(tr("list.role_override", roleText, "%.4f".formatted(entry.getValue()))
                    .withColor(0x808080));
        }
    }

    private static MutableText formatFaction(Faction faction) {
        return tr("faction." + faction.name().toLowerCase(Locale.ROOT)).withColor(faction.displayColor());
    }

    private static MutableText formatRole(Role role) {
        return Harpymodloader.getRoleName(role)
                .withColor(role.color())
                .styled(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(role.identifier().toString()))));
    }
}
