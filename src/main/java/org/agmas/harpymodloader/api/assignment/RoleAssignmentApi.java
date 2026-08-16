package org.agmas.harpymodloader.api.assignment;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Harpy 扩展职业分配公开 API。
 *
 * <p>这个 API 的目标是让扩展模组不再 mixin Harpy 的私有分配函数：
 * 1. 同局互斥：某个职业生成后，本阶段阻止另一个职业继续生成；
 * 2. 单向排斥：A 生成后阻止 B，但 B 不一定阻止 A；
 * 3. 绑定/补位：阶段结束前根据已经生成的职业补充另一名玩家或改写最终职业。</p>
 *
 * <p>注意：这里的规则只影响 Harpy 开局分配流程；局内管理员调试转职仍按指令逻辑执行，
 * 避免调试时被开局随机规则挡住。</p>
 */
public final class RoleAssignmentApi {
    private static final List<RegisteredRoleRule> ROLE_RULES = new ArrayList<>();
    private static final List<RegisteredRolePhaseHandler> BEFORE_PHASE_HANDLERS = new ArrayList<>();
    private static final List<RegisteredRolePhaseHandler> AFTER_PHASE_HANDLERS = new ArrayList<>();
    private static final ThreadLocal<RoleAssignmentSession> CURRENT_SESSION = new ThreadLocal<>();

    private RoleAssignmentApi() {
    }

    public static void registerRule(@NotNull Identifier id, int priority, @NotNull RoleAssignmentRule rule) {
        ROLE_RULES.removeIf(registered -> registered.id().equals(id));
        ROLE_RULES.add(new RegisteredRoleRule(id, priority, rule));
        ROLE_RULES.sort(Comparator.comparingInt(RegisteredRoleRule::priority).reversed());
    }

    public static void registerBeforePhaseHandler(@NotNull Identifier id, @Nullable RoleAssignmentPhase phase, int priority, @NotNull RoleAssignmentPhaseHandler handler) {
        registerPhaseHandler(BEFORE_PHASE_HANDLERS, id, phase, priority, handler);
    }

    public static void registerAfterPhaseHandler(@NotNull Identifier id, @Nullable RoleAssignmentPhase phase, int priority, @NotNull RoleAssignmentPhaseHandler handler) {
        registerPhaseHandler(AFTER_PHASE_HANDLERS, id, phase, priority, handler);
    }

    private static void registerPhaseHandler(
            @NotNull List<RegisteredRolePhaseHandler> handlers,
            @NotNull Identifier id,
            @Nullable RoleAssignmentPhase phase,
            int priority,
            @NotNull RoleAssignmentPhaseHandler handler
    ) {
        handlers.removeIf(registered -> registered.id().equals(id));
        handlers.add(new RegisteredRolePhaseHandler(id, phase, priority, handler));
        handlers.sort(Comparator.comparingInt(RegisteredRolePhaseHandler::priority).reversed());
    }

    /**
     * 注册两个职业的同阶段互斥关系。
     *
     * <p>示例：Hacker 与 Mimic 不希望同局出现时，任意一个先落地，另一个后续就会被跳过。</p>
     */
    public static void registerMutualExclusion(
            @NotNull Identifier id,
            @Nullable RoleAssignmentPhase phase,
            @NotNull Role first,
            @NotNull Role second
    ) {
        registerMutualExclusion(id, phase, () -> true, first, second);
    }

    public static void registerMutualExclusion(
            @NotNull Identifier id,
            @Nullable RoleAssignmentPhase phase,
            @NotNull BooleanSupplier enabled,
            @NotNull Role first,
            @NotNull Role second
    ) {
        registerRule(id, 0, context -> {
            if (!enabled.getAsBoolean() || !matchesPhase(phase, context.phase())) {
                return AssignmentDecision.PASS;
            }
            if (context.role() == first && context.hasAssigned(second)) {
                return AssignmentDecision.DENY;
            }
            if (context.role() == second && context.hasAssigned(first)) {
                return AssignmentDecision.DENY;
            }
            return AssignmentDecision.PASS;
        });
    }

    /**
     * 注册单向排斥关系：blocking 已经生成后，blocked 本阶段不再生成。
     */
    public static void registerOneWayExclusion(
            @NotNull Identifier id,
            @Nullable RoleAssignmentPhase phase,
            @NotNull Role blocked,
            @NotNull Role blocking
    ) {
        registerOneWayExclusion(id, phase, () -> true, blocked, blocking);
    }

    public static void registerOneWayExclusion(
            @NotNull Identifier id,
            @Nullable RoleAssignmentPhase phase,
            @NotNull BooleanSupplier enabled,
            @NotNull Role blocked,
            @NotNull Role blocking
    ) {
        registerRule(id, 0, context -> {
            if (!enabled.getAsBoolean() || !matchesPhase(phase, context.phase())) {
                return AssignmentDecision.PASS;
            }
            return context.role() == blocked && context.hasAssigned(blocking)
                    ? AssignmentDecision.DENY
                    : AssignmentDecision.PASS;
        });
    }

    public static void beginPhase(
            @NotNull RoleAssignmentPhase phase,
            @NotNull ServerWorld serverWorld,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull List<ServerPlayerEntity> players
    ) {
        RoleAssignmentSession session = new RoleAssignmentSession(phase, serverWorld, gameWorldComponent, players);
        CURRENT_SESSION.set(session);
        firePhaseHandlers(BEFORE_PHASE_HANDLERS, session);
    }

    public static void endPhase() {
        RoleAssignmentSession session = CURRENT_SESSION.get();
        if (session == null) {
            return;
        }
        try {
            firePhaseHandlers(AFTER_PHASE_HANDLERS, session);
        } finally {
            CURRENT_SESSION.remove();
        }
    }

    public static boolean canAssignRole(
            int desiredRoleCount,
            @NotNull Role role,
            @NotNull List<ServerPlayerEntity> players,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull World world
    ) {
        RoleAssignmentSession session = CURRENT_SESSION.get();
        if (session == null) {
            return true;
        }

        RoleAssignmentContext context = new RoleAssignmentContext(
                session.phase(),
                desiredRoleCount,
                role,
                players,
                gameWorldComponent,
                world,
                session.assignedRoles()
        );
        for (RegisteredRoleRule registered : ROLE_RULES) {
            if (registered.rule().test(context).denied()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统一写入扩展职业并触发 Harpy 的职业分配事件。
     *
     * <p>Harpy 自己的随机/强制分配和扩展通过阶段回调做的绑定补位都走这里，
     * 这样职业发物品、初始化组件、回放记录等旧事件链仍保持一致。</p>
     */
    public static void assignRole(@NotNull ServerPlayerEntity player, @NotNull Role role, @NotNull GameWorldComponent gameWorldComponent) {
        gameWorldComponent.addRole(player, role);
        Log.info(LogCategory.GENERAL, player.getNameForScoreboard() + " || " + role.identifier());
        recordAssignedRole(role, 1);
        ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
    }

    public static void recordAssignedRole(@NotNull Role role, int count) {
        if (count <= 0) {
            return;
        }
        RoleAssignmentSession session = CURRENT_SESSION.get();
        if (session != null) {
            session.assignedRoles().merge(role, count, Integer::sum);
        }
    }

    private static void firePhaseHandlers(@NotNull List<RegisteredRolePhaseHandler> handlers, @NotNull RoleAssignmentSession session) {
        RoleAssignmentPhaseContext context = new RoleAssignmentPhaseContext(
                session.phase(),
                session.serverWorld(),
                session.gameWorldComponent(),
                session.players(),
                session.assignedRoles()
        );
        for (RegisteredRolePhaseHandler registered : handlers) {
            if (matchesPhase(registered.phase(), session.phase())) {
                registered.handler().handle(context);
            }
        }
    }

    private static boolean matchesPhase(@Nullable RoleAssignmentPhase expected, @NotNull RoleAssignmentPhase actual) {
        return expected == null || expected == actual;
    }

    private record RegisteredRoleRule(Identifier id, int priority, RoleAssignmentRule rule) {
    }

    private record RegisteredRolePhaseHandler(Identifier id, @Nullable RoleAssignmentPhase phase, int priority, RoleAssignmentPhaseHandler handler) {
    }

    private record RoleAssignmentSession(
            RoleAssignmentPhase phase,
            ServerWorld serverWorld,
            GameWorldComponent gameWorldComponent,
            List<ServerPlayerEntity> players,
            Map<Role, Integer> assignedRoles
    ) {
        private RoleAssignmentSession(
                RoleAssignmentPhase phase,
                ServerWorld serverWorld,
                GameWorldComponent gameWorldComponent,
                List<ServerPlayerEntity> players
        ) {
            this(phase, serverWorld, gameWorldComponent, players, new LinkedHashMap<>());
        }
    }
}
