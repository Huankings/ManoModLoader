package org.agmas.harpymodloader.client.role_name;


/**
 * Harpy 准心职业信息与中立同伙规则的客户端聚合入口。
 */
public final class HarpyRoleNameHudHandlers {
    private HarpyRoleNameHudHandlers() {
    }

    public static void register() {
        ObserverRoleDetailsHudHandler.register();
        NeutralAffiliationHudHandler.register();
        KillerNeutralCohortHudHandler.register();
        HarpyBodyHudHandler.register();
    }
}
