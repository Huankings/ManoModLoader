package org.agmas.harpymodloader.api.assignment;

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
 * 词条分配生命周期回调使用的上下文。
 *
 * <p>用于替代扩展模组 mixin {@code assignModifiers} 的 HEAD/TAIL/公告前注入点。
 * 比如强制恋人、强制双重人格这种“随机分配后、公告前改成最终配对”的逻辑，
 * 应注册公告前回调，而不是继续依赖 Harpy 方法内部的字节码位置。</p>
 */
public final class ModifierAssignmentLifecycleContext {
    private final int desiredModifierCount;
    private final ServerWorld serverWorld;
    private final GameWorldComponent gameWorldComponent;
    private final WorldModifierComponent worldModifierComponent;
    private final List<ServerPlayerEntity> players;
    private final Map<Modifier, Integer> assignedModifiers;

    ModifierAssignmentLifecycleContext(
            int desiredModifierCount,
            @NotNull ServerWorld serverWorld,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull WorldModifierComponent worldModifierComponent,
            @NotNull List<ServerPlayerEntity> players,
            @NotNull Map<Modifier, Integer> assignedModifiers
    ) {
        this.desiredModifierCount = desiredModifierCount;
        this.serverWorld = serverWorld;
        this.gameWorldComponent = gameWorldComponent;
        this.worldModifierComponent = worldModifierComponent;
        this.players = Collections.unmodifiableList(players);
        this.assignedModifiers = assignedModifiers;
    }

    public int desiredModifierCount() {
        return desiredModifierCount;
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
        return Collections.unmodifiableMap(assignedModifiers);
    }

    /**
     * 统一补发一个词条并触发 Harpy 词条事件。
     *
     * <p>成对词条如果已经手动维护自己的配对组件，可以继续直接操作组件；
     * 只有希望完整复用 Harpy {@code ModifierAssigned} 事件链时才调用这里。</p>
     */
    public void assignModifier(@NotNull ServerPlayerEntity player, @NotNull Modifier modifier) {
        ModifierAssignmentApi.assignModifier(player, modifier, worldModifierComponent);
    }
}
