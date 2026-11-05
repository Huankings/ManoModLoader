package org.agmas.harpymodloader.client.role_name;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.client.gui.RoleNameHudApi;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

/**
 * 非存活观察者准心处的“职业名 + 词条”详情行。
 *
 * <p>旧实现通过两个 RoleNameRenderer mixin 注入点把目标职业暂存到全局静态字段，
 * 很容易在淡出帧、射线切换或 Wathe 调整绘制顺序后串目标。现在 provider 的上下文已经包含
 * 同一帧的目标玩家和职业，词条也直接从该目标读取，不再保存跨调用临时状态。</p>
 */
public final class ObserverRoleDetailsHudHandler {
    private ObserverRoleDetailsHudHandler() {
    }

    public static void register() {
        RoleNameHudApi.registerRoleDetailsLine(
                Identifier.of(Harpymodloader.MOD_ID, "role_name/observer_role_details"),
                RoleNameHudApi.DEFAULT_PRIORITY,
                context -> {
                    if (!GameFunctions.isPlayerSpectatingOrCreative(context.viewer())) {
                        return null;
                    }
                    Role role = context.targetRole() == null ? WatheRoles.CIVILIAN : context.targetRole();
                    MutableText text = Harpymodloader.getRoleName(role);
                    var modifiers = WorldModifierComponent.KEY.get(context.target().getWorld())
                            .getModifiers(context.target());
                    if (modifiers != null) {
                        for (Modifier modifier : modifiers) {
                            text.append(Text.literal(" [")
                                    .append(modifier.getName())
                                    .append("]")
                                    .withColor(modifier.color));
                        }
                    }
                    return new RoleNameHudApi.RoleNameLine(text, role.color());
                }
        );
    }
}
