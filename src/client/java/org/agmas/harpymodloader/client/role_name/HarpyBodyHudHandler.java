package org.agmas.harpymodloader.client.role_name;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.api.client.gui.PlayerBodyHudApi;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.api.neutral.NeutralRoleCategoryApi;
import org.agmas.harpymodloader.component.BodyModifierSnapshotComponent;
import org.agmas.harpymodloader.modifiers.Modifier;

/** Harpy 对 Wathe 尸体身份行和中立细分类阵营行的纯客户端接入。 */
public final class HarpyBodyHudHandler {
    private HarpyBodyHudHandler() {
    }

    public static void register() {
        PlayerBodyHudApi.registerLineProvider(
                Identifier.of(Harpymodloader.MOD_ID, "body_identity"),
                PlayerBodyHudApi.BodyHudLine.IDENTITY,
                PlayerBodyHudApi.DEFAULT_PRIORITY + 100,
                (context, line) -> {
                    Role role = WatheRoles.getRole(Identifier.tryParse(context.body().getPlayerRoleId()));
                    if (role == null) {
                        role = WatheRoles.CIVILIAN;
                    }
                    MutableText text = Text.translatable("hud.wathe.body.identity")
                            .withColor(Colors.RED)
                            .append(Harpymodloader.getRoleName(role).withColor(role.color()));
                    for (Modifier modifier : BodyModifierSnapshotComponent.KEY.get(context.body()).resolveModifiers()) {
                        text.append(Text.literal(" [")
                                .append(modifier.getName())
                                .append("]")
                                .withColor(modifier.color()));
                    }
                    return text;
                }
        );
        PlayerBodyHudApi.registerLineProvider(
                Identifier.of(Harpymodloader.MOD_ID, "body_neutral_affiliation"),
                PlayerBodyHudApi.BodyHudLine.AFFILIATION,
                PlayerBodyHudApi.DEFAULT_PRIORITY + 100,
                (context, line) -> {
                    Role role = WatheRoles.getRole(Identifier.tryParse(context.body().getPlayerRoleId()));
                    var category = NeutralRoleCategoryApi.resolveCategory(role);
                    if (category == null) {
                        return null;
                    }
                    return Text.translatable("hud.wathe.role_name.affiliation_format",
                            Text.translatable(category.translationKey())).withColor(category.displayColor());
                }
        );
    }
}
