package org.agmas.harpymodloader.api.assignment;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ModifierAssigned;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Harpy 扩展词条分配公开 API。
 *
 * <p>它分成两层能力：</p>
 * <p>1. 候选规则：在 Harpy 准备把某词条写给某玩家前，判断是否允许；</p>
 * <p>2. 生命周期回调：在词条分配开始、公告前、分配结束时，让扩展做强制配对、动态上限刷新等收尾。</p>
 */
public final class ModifierAssignmentApi {
    private static final List<RegisteredModifierRule> MODIFIER_RULES = new ArrayList<>();
    private static final List<RegisteredModifierLifecycleHandler> BEFORE_ASSIGNMENT_HANDLERS = new ArrayList<>();
    private static final List<RegisteredModifierLifecycleHandler> BEFORE_ANNOUNCEMENT_HANDLERS = new ArrayList<>();
    private static final List<RegisteredModifierLifecycleHandler> AFTER_ASSIGNMENT_HANDLERS = new ArrayList<>();
    private static final ThreadLocal<ModifierAssignmentSession> CURRENT_SESSION = new ThreadLocal<>();

    private ModifierAssignmentApi() {
    }

    public static void registerRule(@NotNull Identifier id, int priority, @NotNull ModifierAssignmentRule rule) {
        MODIFIER_RULES.removeIf(registered -> registered.id().equals(id));
        MODIFIER_RULES.add(new RegisteredModifierRule(id, priority, rule));
        MODIFIER_RULES.sort(Comparator.comparingInt(RegisteredModifierRule::priority).reversed());
    }

    public static void registerBeforeAssignmentHandler(@NotNull Identifier id, int priority, @NotNull ModifierAssignmentLifecycleHandler handler) {
        registerLifecycleHandler(BEFORE_ASSIGNMENT_HANDLERS, id, priority, handler);
    }

    public static void registerBeforeAnnouncementHandler(@NotNull Identifier id, int priority, @NotNull ModifierAssignmentLifecycleHandler handler) {
        registerLifecycleHandler(BEFORE_ANNOUNCEMENT_HANDLERS, id, priority, handler);
    }

    public static void registerAfterAssignmentHandler(@NotNull Identifier id, int priority, @NotNull ModifierAssignmentLifecycleHandler handler) {
        registerLifecycleHandler(AFTER_ASSIGNMENT_HANDLERS, id, priority, handler);
    }

    private static void registerLifecycleHandler(
            @NotNull List<RegisteredModifierLifecycleHandler> handlers,
            @NotNull Identifier id,
            int priority,
            @NotNull ModifierAssignmentLifecycleHandler handler
    ) {
        handlers.removeIf(registered -> registered.id().equals(id));
        handlers.add(new RegisteredModifierLifecycleHandler(id, priority, handler));
        handlers.sort(Comparator.comparingInt(RegisteredModifierLifecycleHandler::priority).reversed());
    }

    /**
     * 注册“某词条不能给某职业”的动态排斥规则。
     */
    public static void registerModifierExcludesRole(
            @NotNull Identifier id,
            int priority,
            @NotNull Modifier modifier,
            @NotNull Role excludedRole
    ) {
        registerModifierExcludesRole(id, priority, () -> true, modifier, excludedRole);
    }

    public static void registerModifierExcludesRole(
            @NotNull Identifier id,
            int priority,
            @NotNull BooleanSupplier enabled,
            @NotNull Modifier modifier,
            @NotNull Role excludedRole
    ) {
        registerRule(id, priority, context -> enabled.getAsBoolean()
                && context.modifier() == modifier
                && context.playerRole() == excludedRole
                ? AssignmentDecision.DENY
                : AssignmentDecision.PASS);
    }

    /**
     * 注册“某词条只能绑定某职业”的动态规则。
     */
    public static void registerModifierRequiresRole(
            @NotNull Identifier id,
            int priority,
            @NotNull Modifier modifier,
            @NotNull Role requiredRole
    ) {
        registerModifierRequiresRole(id, priority, () -> true, modifier, requiredRole);
    }

    public static void registerModifierRequiresRole(
            @NotNull Identifier id,
            int priority,
            @NotNull BooleanSupplier enabled,
            @NotNull Modifier modifier,
            @NotNull Role requiredRole
    ) {
        registerRule(id, priority, context -> enabled.getAsBoolean()
                && context.modifier() == modifier
                && context.playerRole() != requiredRole
                ? AssignmentDecision.DENY
                : AssignmentDecision.PASS);
    }

    /**
     * 注册两个词条不能同时出现在同一个玩家身上的互斥规则。
     */
    public static void registerModifierMutualExclusion(
            @NotNull Identifier id,
            int priority,
            @NotNull Modifier first,
            @NotNull Modifier second
    ) {
        registerModifierMutualExclusion(id, priority, () -> true, first, second);
    }

    public static void registerModifierMutualExclusion(
            @NotNull Identifier id,
            int priority,
            @NotNull BooleanSupplier enabled,
            @NotNull Modifier first,
            @NotNull Modifier second
    ) {
        registerRule(id, priority, context -> {
            if (!enabled.getAsBoolean()) {
                return AssignmentDecision.PASS;
            }
            if (context.modifier() == first && context.playerHasModifier(second)) {
                return AssignmentDecision.DENY;
            }
            if (context.modifier() == second && context.playerHasModifier(first)) {
                return AssignmentDecision.DENY;
            }
            return AssignmentDecision.PASS;
        });
    }

    /**
     * 注册单向词条排斥：blocking 已在玩家身上时，blocked 不再给该玩家。
     */
    public static void registerModifierOneWayExclusion(
            @NotNull Identifier id,
            int priority,
            @NotNull Modifier blocked,
            @NotNull Modifier blocking
    ) {
        registerModifierOneWayExclusion(id, priority, () -> true, blocked, blocking);
    }

    public static void registerModifierOneWayExclusion(
            @NotNull Identifier id,
            int priority,
            @NotNull BooleanSupplier enabled,
            @NotNull Modifier blocked,
            @NotNull Modifier blocking
    ) {
        registerRule(id, priority, context -> enabled.getAsBoolean()
                && context.modifier() == blocked
                && context.playerHasModifier(blocking)
                ? AssignmentDecision.DENY
                : AssignmentDecision.PASS);
    }

    public static void beginAssignment(
            int desiredModifierCount,
            @NotNull ServerWorld serverWorld,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull WorldModifierComponent worldModifierComponent,
            @NotNull List<ServerPlayerEntity> players
    ) {
        ModifierAssignmentSession session = new ModifierAssignmentSession(desiredModifierCount, serverWorld, gameWorldComponent, worldModifierComponent, players);
        CURRENT_SESSION.set(session);
        fireLifecycleHandlers(BEFORE_ASSIGNMENT_HANDLERS, session);
    }

    public static void beforeAnnouncements() {
        ModifierAssignmentSession session = CURRENT_SESSION.get();
        if (session != null) {
            fireLifecycleHandlers(BEFORE_ANNOUNCEMENT_HANDLERS, session);
        }
    }

    public static void endAssignment() {
        ModifierAssignmentSession session = CURRENT_SESSION.get();
        if (session == null) {
            return;
        }
        try {
            fireLifecycleHandlers(AFTER_ASSIGNMENT_HANDLERS, session);
        } finally {
            CURRENT_SESSION.remove();
        }
    }

    public static boolean canAssignModifier(@NotNull Modifier modifier, @NotNull ServerPlayerEntity player) {
        ModifierAssignmentSession session = CURRENT_SESSION.get();
        if (session == null) {
            return true;
        }
        ModifierAssignmentContext context = new ModifierAssignmentContext(
                session.desiredModifierCount(),
                modifier,
                player,
                session.serverWorld(),
                session.gameWorldComponent(),
                session.worldModifierComponent(),
                session.players(),
                session.assignedModifiers()
        );
        for (RegisteredModifierRule registered : MODIFIER_RULES) {
            if (registered.rule().test(context).denied()) {
                return false;
            }
        }
        return true;
    }

    public static void assignModifier(@NotNull ServerPlayerEntity player, @NotNull Modifier modifier, @NotNull WorldModifierComponent worldModifierComponent) {
        worldModifierComponent.addModifier(player.getUuid(), modifier);
        recordAssignedModifier(modifier, 1);
        ModifierAssigned.EVENT.invoker().assignModifier(player, modifier);
    }

    public static void recordAssignedModifier(@NotNull Modifier modifier, int count) {
        if (count <= 0) {
            return;
        }
        ModifierAssignmentSession session = CURRENT_SESSION.get();
        if (session != null) {
            session.assignedModifiers().merge(modifier, count, Integer::sum);
        }
    }

    private static void fireLifecycleHandlers(@NotNull List<RegisteredModifierLifecycleHandler> handlers, @NotNull ModifierAssignmentSession session) {
        ModifierAssignmentLifecycleContext context = new ModifierAssignmentLifecycleContext(
                session.desiredModifierCount(),
                session.serverWorld(),
                session.gameWorldComponent(),
                session.worldModifierComponent(),
                session.players(),
                session.assignedModifiers()
        );
        for (RegisteredModifierLifecycleHandler registered : handlers) {
            registered.handler().handle(context);
        }
    }

    private record RegisteredModifierRule(Identifier id, int priority, ModifierAssignmentRule rule) {
    }

    private record RegisteredModifierLifecycleHandler(Identifier id, int priority, ModifierAssignmentLifecycleHandler handler) {
    }

    private record ModifierAssignmentSession(
            int desiredModifierCount,
            ServerWorld serverWorld,
            GameWorldComponent gameWorldComponent,
            WorldModifierComponent worldModifierComponent,
            List<ServerPlayerEntity> players,
            Map<Modifier, Integer> assignedModifiers
    ) {
        private ModifierAssignmentSession(
                int desiredModifierCount,
                ServerWorld serverWorld,
                GameWorldComponent gameWorldComponent,
                WorldModifierComponent worldModifierComponent,
                List<ServerPlayerEntity> players
        ) {
            this(desiredModifierCount, serverWorld, gameWorldComponent, worldModifierComponent, players, new LinkedHashMap<>());
        }
    }
}
