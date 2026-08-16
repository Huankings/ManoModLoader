package org.agmas.harpymodloader.api.assignment;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 职业分配阶段回调使用的上下文。
 *
 * <p>这个上下文允许扩展在 Harpy 某个替换阶段结束前补充最终职业，
 * 例如“某个中立职业随机到 1 人后，再从合法候选里绑定生成第 2 人”。</p>
 */
public final class RoleAssignmentPhaseContext {
    private final RoleAssignmentPhase phase;
    private final ServerWorld serverWorld;
    private final GameWorldComponent gameWorldComponent;
    private final List<ServerPlayerEntity> players;
    private final Map<Role, Integer> assignedRoles;

    RoleAssignmentPhaseContext(
            @NotNull RoleAssignmentPhase phase,
            @NotNull ServerWorld serverWorld,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull List<ServerPlayerEntity> players,
            @NotNull Map<Role, Integer> assignedRoles
    ) {
        this.phase = phase;
        this.serverWorld = serverWorld;
        this.gameWorldComponent = gameWorldComponent;
        this.players = Collections.unmodifiableList(players);
        this.assignedRoles = assignedRoles;
    }

    public RoleAssignmentPhase phase() {
        return phase;
    }

    public ServerWorld serverWorld() {
        return serverWorld;
    }

    public GameWorldComponent gameWorldComponent() {
        return gameWorldComponent;
    }

    public List<ServerPlayerEntity> players() {
        return players;
    }

    public Map<Role, Integer> assignedRolesView() {
        return Collections.unmodifiableMap(assignedRoles);
    }

    public int assignedCount(Role role) {
        return assignedRoles.getOrDefault(role, 0);
    }

    public boolean hasAssigned(Role role) {
        return assignedCount(role) > 0;
    }

    /**
     * 在当前阶段内补充写入一个职业，并走 Harpy 的统一事件链。
     *
     * <p>扩展不要直接调用 {@code gameWorldComponent.addRole + ModdedRoleAssigned} 拼一份；
     * 统一从这里走可以让 Harpy 记录本阶段已经生成的职业，后续排斥/绑定规则也能读到。</p>
     */
    public void assignRole(@NotNull ServerPlayerEntity player, @NotNull Role role) {
        RoleAssignmentApi.assignRole(player, role, gameWorldComponent);
    }
}
