package org.agmas.harpymodloader.neutral;

import org.agmas.harpymodloader.api.neutral.NeutralRoleCategories;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategoryApi;

/**
 * Harpy 内置中立分类的公共初始化入口。
 */
public final class HarpyNeutralBootstrap {
    private static boolean initialized;

    private HarpyNeutralBootstrap() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        NeutralRoleCategoryApi.registerCategory(NeutralRoleCategories.KILLER_NEUTRAL);
        NeutralRoleCategoryApi.registerCategory(NeutralRoleCategories.ORDINARY_NEUTRAL);
        NeutralRoleCategoryApi.registerCategory(NeutralRoleCategories.INDEPENDENT_NEUTRAL);
    }
}
