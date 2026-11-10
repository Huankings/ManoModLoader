package org.agmas.harpymodloader.component;

import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 尸体生成瞬间冻结的 Harpy 词条快照。
 *
 * <p>真实尸体保存死者当时的词条；伪尸体由 {@code HarpyBodyInfoHandler} 按伪造职业生成
 * 合法数量的随机假词条。这里只保存词条 ID，客户端再从当前已注册表解析名称和颜色，
 * 既避免同步整份 Modifier 对象，也能兼容语言切换。</p>
 */
public final class BodyModifierSnapshotComponent implements AutoSyncedComponent {
    public static final ComponentKey<BodyModifierSnapshotComponent> KEY =
            ComponentRegistry.getOrCreate(Identifier.of(Harpymodloader.MOD_ID, "body_modifier_snapshot"), BodyModifierSnapshotComponent.class);

    private final PlayerBodyEntity body;
    private final List<Identifier> modifierIds = new ArrayList<>();

    public BodyModifierSnapshotComponent(@NotNull PlayerBodyEntity body) {
        this.body = body;
    }

    public List<Identifier> getModifierIds() {
        return List.copyOf(modifierIds);
    }

    public void setModifierIds(List<Identifier> ids) {
        modifierIds.clear();
        if (ids != null) {
            modifierIds.addAll(ids);
        }
        sync();
    }

    public List<Modifier> resolveModifiers() {
        List<Modifier> result = new ArrayList<>();
        for (Identifier id : modifierIds) {
            for (Modifier modifier : HMLModifiers.MODIFIERS) {
                if (modifier.identifier().equals(id)) {
                    result.add(modifier);
                    break;
                }
            }
        }
        return result;
    }

    public void sync() {
        KEY.sync(body);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Identifier id : modifierIds) {
            list.add(net.minecraft.nbt.NbtString.of(id.toString()));
        }
        tag.put("modifiers", list);
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        modifierIds.clear();
        for (var element : tag.getList("modifiers", 8)) {
            Identifier id = Identifier.tryParse(element.asString());
            if (id != null) {
                modifierIds.add(id);
            }
        }
    }
}
