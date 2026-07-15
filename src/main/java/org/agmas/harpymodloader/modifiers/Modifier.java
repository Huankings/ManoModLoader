package org.agmas.harpymodloader.modifiers;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class Modifier {

    public Identifier identifier;
    public int color;
    public ArrayList<Role> cannotBeAppliedTo;
    public ArrayList<Role> canOnlyBeAppliedTo;
    public boolean killerOnly;
    public boolean civilianOnly;
    private @Nullable ModifierEligibilityPredicate eligibilityPredicate;

    public Modifier(Identifier identifier, int color, ArrayList<Role> cannotBeAppliedTo, ArrayList<Role> canOnlyBeAppliedTo, boolean killerOnly, boolean civilianOnly) {
        this.identifier = identifier;
        this.color = color;
        this.cannotBeAppliedTo = cannotBeAppliedTo;
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
        this.killerOnly = killerOnly;
        this.civilianOnly = civilianOnly;
    }

    public Identifier identifier() {
        return this.identifier;
    }

    public MutableText getName() {
        return getName(false);
    }

    public MutableText getName(boolean color) {
        Log.info(LogCategory.GENERAL, Language.getInstance().hasTranslation("announcement.modifier." + identifier().getPath())+"");
        if (!Language.getInstance().hasTranslation("announcement.modifier." + identifier().toTranslationKey()) && Language.getInstance().hasTranslation("announcement.modifier." + identifier().getPath())) {
            return Text.translatable("announcement.modifier." + identifier().getPath());
        }
        final MutableText text = Text.translatable("announcement.modifier." + identifier().toTranslationKey());
        if (color) {
            return text.withColor(color());
        }
        return text;
    }

    public int color() {
        return this.color;
    }

    public ArrayList<Role> canOnlyBeAppliedTo() {
        return canOnlyBeAppliedTo;
    }

    public ArrayList<Role> cannotBeAppliedTo() {
        return cannotBeAppliedTo;
    }

    public void setCannotBeAppliedTo(ArrayList<Role> cannotBeAppliedTo) {
        this.cannotBeAppliedTo = cannotBeAppliedTo;
    }

    public void setCanOnlyBeAppliedTo(ArrayList<Role> canOnlyBeAppliedTo) {
        this.canOnlyBeAppliedTo = canOnlyBeAppliedTo;
    }

    /**
     * 设置词条的动态适用条件。
     *
     * <p>旧版 HarpyModLoader 只能用 canOnlyBeAppliedTo / cannotBeAppliedTo 这种静态职业列表限制词条。
     * 这会导致 Taskmaster、Magnate 这类“取决于其他模组是否注册了经济能力”的词条必须手动维护跨模组名单。
     * 现在保留旧字段用于兼容，同时允许词条在分配时根据玩家当前职业、Wathe API 注册结果、配置开关等动态判断。</p>
     */
    public Modifier setEligibilityPredicate(@Nullable ModifierEligibilityPredicate eligibilityPredicate) {
        this.eligibilityPredicate = eligibilityPredicate;
        return this;
    }

    public boolean canApplyTo(@NotNull GameWorldComponent gameWorldComponent, @NotNull PlayerEntity player) {
        Role role = gameWorldComponent.getRole(player);
        boolean valid = true;

        if (this.canOnlyBeAppliedTo != null && role != null) {
            valid = this.canOnlyBeAppliedTo.contains(role);
        }
        if (this.cannotBeAppliedTo != null && role != null) {
            valid = !this.cannotBeAppliedTo.contains(role);
        }
        if (!valid) {
            return false;
        }

        if (this.killerOnly) {
            valid = gameWorldComponent.canUseKillerFeatures(player);
        }
        if (this.civilianOnly) {
            valid = !gameWorldComponent.canUseKillerFeatures(player);
        }
        if (!valid) {
            return false;
        }

        return this.eligibilityPredicate == null || this.eligibilityPredicate.canApply(gameWorldComponent, player, this);
    }

    @FunctionalInterface
    public interface ModifierEligibilityPredicate {
        boolean canApply(@NotNull GameWorldComponent gameWorldComponent, @NotNull PlayerEntity player, @NotNull Modifier modifier);
    }
}
