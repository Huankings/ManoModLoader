package org.agmas.harpymodloader.api.assignment;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 词条分配规则读取的上下文。
 *
 * <p>它既能读取目标玩家当前职业，也能读取玩家已经持有的词条，
 * 因此可以表达“某词条排斥某职业”“某词条只能绑定某职业”“两个词条不能同人生成”等规则。</p>
 */
public final class ModifierAssignmentContext {
    private final int desiredModifierCount;
    private final Modifier modifier;
    private final ServerPlayerEntity player;
    private final ServerWorld serverWorld;
    private final GameWorldComponent gameWorldComponent;
    private final WorldModifierComponent worldModifierComponent;
    private final List<ServerPlayerEntity> players;
    private final Map<Modifier, Integer> assignedModifiers;

    ModifierAssignmentContext(
            int desiredModifierCount,
            @NotNull Modifier modifier,
            @NotNull ServerPlayerEntity player,
            @NotNull ServerWorld serverWorld,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull WorldModifierComponent worldModifierComponent,
            @NotNull List<ServerPlayerEntity> players,
            @NotNull Map<Modifier, Integer> assignedModifiers
    ) {
        this.desiredModifierCount = desiredModifierCount;
        this.modifier = modifier;
        this.player = player;
        this.serverWorld = serverWorld;
        this.gameWorldComponent = gameWorldComponent;
        this.worldModifierComponent = worldModifierComponent;
        this.players = Collections.unmodifiableList(players);
        this.assignedModifiers = Collections.unmodifiableMap(assignedModifiers);
    }

    public int desiredModifierCount() {
        return desiredModifierCount;
    }

    public Modifier modifier() {
        return modifier;
    }

    public ServerPlayerEntity player() {
        return player;
    }

    public ServerWorld serverWorld() {
        return serverWorld;
    }

    public GameWorldComponent gameWorldComponent() {
        return gameWorldComponent;
    }

    public WorldModifierComponent worldModifierComponent() {
        return worldModifierComponent;
    }

    public List<ServerPlayerEntity> players() {
        return players;
    }

    public Map<Modifier, Integer> assignedModifiersView() {
        return assignedModifiers;
    }

    public Role playerRole() {
        return gameWorldComponent.getRole(player);
    }

    public boolean playerHasModifier(Modifier modifier) {
        return worldModifierComponent.isModifier(player, modifier);
    }

    public int assignedCount(Modifier modifier) {
        return assignedModifiers.getOrDefault(modifier, 0);
    }
}
