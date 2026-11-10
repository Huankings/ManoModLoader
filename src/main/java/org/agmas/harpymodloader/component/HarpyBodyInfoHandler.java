package org.agmas.harpymodloader.component;

import dev.doctor4t.wathe.api.body.BodyInfoInitializedContext;
import dev.doctor4t.wathe.api.body.PlayerBodyInfoApi;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Harpy 尸体词条快照和尸体身份/中立阵营 HUD 接入。
 */
public final class HarpyBodyInfoHandler {
    private static boolean initialized;

    private HarpyBodyInfoHandler() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        PlayerBodyInfoApi.registerInitializedHandler(
                Identifier.of(Harpymodloader.MOD_ID, "body_modifier_snapshot"),
                PlayerBodyInfoApi.DEFAULT_PRIORITY,
                HarpyBodyInfoHandler::captureModifiers
        );
    }

    private static void captureModifiers(BodyInfoInitializedContext context) {
        BodyModifierSnapshotComponent component = BodyModifierSnapshotComponent.KEY.get(context.body());
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(context.body().getWorld());
        var sourceUuid = context.snapshot().modifierSourceUuid();
        if (sourceUuid == null || gameWorld.getRole(sourceUuid) == null) {
            component.setModifierIds(List.of());
            return;
        }

        List<Modifier> actual = HarpyModLoaderAccess.getModifiers(context.body(), sourceUuid);
        if (!context.snapshot().synthetic()) {
            component.setModifierIds(actual.stream().map(Modifier::identifier).toList());
            return;
        }

        Role fakeRole = WatheRoles.getRole(context.snapshot().roleId());
        if (fakeRole == null || actual.isEmpty()) {
            component.setModifierIds(List.of());
            return;
        }

        List<Modifier> candidates = new ArrayList<>();
        for (Modifier modifier : HMLModifiers.MODIFIERS) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(modifier.identifier().toString())) {
                continue;
            }
            if (modifier.canApplyToSnapshot(fakeRole)) {
                candidates.add(modifier);
            }
        }
        /*
         * 同一具伪尸体在存档重载或时停回溯后必须得到同一组假词条，
         * 否则观察者可以通过反复回溯看见词条跳变并识破伪造。
         */
        long seed = context.body().getUuid().getMostSignificantBits()
                ^ context.body().getUuid().getLeastSignificantBits()
                ^ fakeRole.identifier().hashCode();
        Collections.shuffle(candidates, new Random(seed));
        component.setModifierIds(candidates.stream().limit(actual.size()).map(Modifier::identifier).toList());
    }

    /** 只在服务端初始化阶段读取当前词条，避免客户端自行推断死亡时快照。 */
    private static final class HarpyModLoaderAccess {
        private static List<Modifier> getModifiers(dev.doctor4t.wathe.entity.PlayerBodyEntity body, java.util.UUID uuid) {
            return org.agmas.harpymodloader.component.WorldModifierComponent.KEY.get(body.getWorld()).getModifiers(uuid);
        }
    }

}
