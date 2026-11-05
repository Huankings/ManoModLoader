package org.agmas.harpymodloader.client.role_name;

import dev.doctor4t.wathe.api.client.gui.RoleNameHudApi;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategory;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategoryApi;

/**
 * 中立分类声明“显示为杀手同伙”后的单向目标规则。
 *
 * <p>这里只改变 target 在有资格查看同伙的 viewer 眼里的表现，不调用 registerCohortState，
 * 因此杀手中立不会仅凭分类就反向识别真正杀手。Hacker、Executioner、Killer Queen 等确需
 * 双向识别的职业仍应在自己的扩展中单独注册 CohortState。</p>
 */
public final class KillerNeutralCohortHudHandler {
    private KillerNeutralCohortHudHandler() {
    }

    public static void register() {
        RoleNameHudApi.registerCohortTargetState(
                Identifier.of(Harpymodloader.MOD_ID, "role_name/killer_neutral_cohort_target"),
                RoleNameHudApi.DEFAULT_PRIORITY + 100,
                (viewer, target, vanillaValue) -> {
                    var role = GameWorldComponent.KEY.get(target.getWorld()).getRole(target);
                    NeutralRoleCategory category = NeutralRoleCategoryApi.resolveCategory(role);
                    return category != null && category.showsAsKillerCohortTarget() ? true : null;
                }
        );
    }
}
