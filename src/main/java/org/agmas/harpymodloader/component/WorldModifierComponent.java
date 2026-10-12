package org.agmas.harpymodloader.component;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

public class WorldModifierComponent implements AutoSyncedComponent {
    public static final ComponentKey<WorldModifierComponent> KEY = ComponentRegistry.getOrCreate(Identifier.of(Harpymodloader.MOD_ID, "modifier"), WorldModifierComponent.class);
    private final World world;
    public HashMap<UUID, ArrayList<Modifier>> modifiers = new HashMap<>();


    public WorldModifierComponent(World world) {
        this.world = world;
    }

    @Deprecated
    public boolean isRole(@NotNull PlayerEntity player, Modifier modifier) {
        return isModifier(player, modifier);
    }

    @Deprecated
    public boolean isRole(@NotNull UUID uuid, Modifier modifier) {
        return isModifier(uuid, modifier);
    }

    public boolean isModifier(@NotNull PlayerEntity player, Modifier modifier) {
        return this.isModifier(player.getUuid(), modifier);
    }

    public boolean isModifier(@NotNull UUID uuid, Modifier modifier) {
        return getModifiers(uuid).contains(modifier);
    }

    public HashMap<UUID, ArrayList<Modifier>> getModifiers() {
        return this.modifiers;
    }

    public ArrayList<Modifier> getModifiers(PlayerEntity player) {
        return this.getModifiers(player.getUuid());
    }

    public ArrayList<Modifier> getModifiers(UUID uuid) {
        if (!modifiers.containsKey(uuid)) modifiers.put(uuid, new ArrayList<>());
        return this.modifiers.get(uuid);
    }

    public List<UUID> getAllWithModifier(Modifier modifier) {
        List<UUID> ret = new ArrayList();
        this.modifiers.forEach((uuid, playerModifier) -> {
            if (playerModifier.contains(modifier)) {
                ret.add(uuid);
            }
        });
        return ret;
    }

    public void setModifiers(List<UUID> players, Modifier modifier) {

        for (UUID player : players) {
            addModifier(player, modifier);
        }

    }

    public boolean addModifier(UUID player, Modifier modifier) {
        /*
         * 词条表过去依赖每 tick 全量广播，因此写入点可以直接修改公开 List。
         * 改成事件驱动同步后，组件必须保证同一个词条不会重复写入；重复项不仅会
         * 影响玩法判断，还会继续放大携带全部玩家 UUID 的同步包。
         */
        ArrayList<Modifier> playerModifiers = getModifiers(player);
        if (playerModifiers.contains(modifier)) {
            return false;
        }
        playerModifiers.add(modifier);
        return true;
    }

    public boolean removeModifier(UUID player, Modifier modifier) {
        ArrayList<Modifier> playerModifiers = this.modifiers.get(player);
        if (playerModifiers == null) {
            return false;
        }
        boolean changed = playerModifiers.removeIf(modifier::equals);
        if (playerModifiers.isEmpty()) {
            this.modifiers.remove(player);
        }
        return changed;
    }

    public boolean clearModifiers(UUID player) {
        return this.modifiers.remove(player) != null;
    }

    public boolean clearAllModifiers() {
        if (this.modifiers.isEmpty()) {
            return false;
        }
        this.modifiers.clear();
        return true;
    }


    @Override
    public void readFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {

        modifiers.clear();
        for (Modifier modifier : HMLModifiers.MODIFIERS) {
            setModifiers(this.uuidListFromNbt(nbtCompound, modifier.identifier().toString()), modifier);
        }
    }

    @Override
    public void writeToNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {

        for (Modifier modifier : HMLModifiers.MODIFIERS) {
            nbtCompound.put(modifier.identifier().toString(), this.nbtFromUuidList(getAllWithModifier(modifier)));
        }
    }

    public void sync() {
        /*
         * 这是整局所有玩家词条的世界组件，单次包会随词条数和玩家数增长。
         * 调用方应在一次批量分配或清理完成后只调用一次，严禁重新放回 serverTick。
         */
        KEY.sync(this.world);
    }

    private ArrayList<UUID> uuidListFromNbt(NbtCompound nbtCompound, String listName) {
        ArrayList<UUID> ret = new ArrayList();

        for (NbtElement e : nbtCompound.getList(listName, 11)) {
            ret.add(NbtHelper.toUuid(e));
        }

        return ret;
    }

    private NbtList nbtFromUuidList(List<UUID> list) {
        NbtList ret = new NbtList();

        for (UUID player : list) {
            ret.add(NbtHelper.fromUuid(player));
        }

        return ret;
    }
}
