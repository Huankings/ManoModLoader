package org.agmas.harpymodloader.api.assignment;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 职业分配规则读取的上下文。
 *
 * <p>这里只暴露只读视图，避免扩展在“是否允许分配”的判定阶段直接改职业表。
 * 需要绑定生成、补第二个职业、覆盖最终结果时，应该注册阶段回调并使用
 * {@link RoleAssignmentPhaseContext#assignRole(ServerPlayerEntity, Role)}。</p>
 */
public final class RoleAssignmentContext {
    private final RoleAssignmentPhase phase;
    private final int desiredRoleCount;
    private final Role role;
    private final List<ServerPlayerEntity> players;
    private final GameWorldComponent gameWorldComponent;
    private final World world;
    private final Map<Role, Integer> assignedRoles;

    RoleAssignmentContext(
            @NotNull RoleAssignmentPhase phase,
            int desiredRoleCount,
            @NotNull Role role,
            @NotNull List<ServerPlayerEntity> players,
            @NotNull GameWorldComponent gameWorldComponent,
            @NotNull World world,
            @NotNull Map<Role, Integer> assignedRoles
    ) {
        this.phase = phase;
        this.desiredRoleCount = desiredRoleCount;
        this.role = role;
        this.players = Collections.unmodifiableList(players);
        this.gameWorldComponent = gameWorldComponent;
        this.world = world;
        this.assignedRoles = Collections.unmodifiableMap(assignedRoles);
    }

    public RoleAssignmentPhase phase() {
        return phase;
    }

    public int desiredRoleCount() {
        return desiredRoleCount;
    }

    public Role role() {
        return role;
    }

    public List<ServerPlayerEntity> players() {
        return players;
    }

    public GameWorldComponent gameWorldComponent() {
        return gameWorldComponent;
    }

    public World world() {
        return world;
    }

    public Map<Role, Integer> assignedRolesView() {
        return assignedRoles;
    }

    public int assignedCount(Role role) {
        return assignedRoles.getOrDefault(role, 0);
    }

    public boolean hasAssigned(Role role) {
        return assignedCount(role) > 0;
    }
}
