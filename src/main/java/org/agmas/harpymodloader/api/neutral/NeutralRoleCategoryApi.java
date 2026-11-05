package org.agmas.harpymodloader.api.neutral;

import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HarpyModLoader 的中立细分类公开 API。
 *
 * <p>扩展先用 {@link #registerCategory(NeutralRoleCategory)} 注册分类定义，再用
 * {@link #registerRole(Identifier, int, Role, Identifier)} 把具体职业归入分类。
 * 职业归类规则按 priority 从高到低执行；同 priority 下后注册者优先，同 id 会替换旧规则。</p>
 *
 * <p>分类只对 {@link Faction#NEUTRAL} 生效，不会修改 Wathe 的真实阵营、分配池、权重、结算或胜利。
 * 需要动态分类时可使用 {@link #registerResolver(Identifier, int, Resolver)}，返回 {@code null}
 * 表示继续询问低优先级规则。</p>
 */
public final class NeutralRoleCategoryApi {
    public static final int DEFAULT_PRIORITY = 0;

    private static final Comparator<Entry> ENTRY_COMPARATOR =
            Comparator.comparingInt(Entry::priority)
                    .reversed()
                    .thenComparing(Comparator.comparingLong(Entry::order).reversed());
    private static final Map<Identifier, NeutralRoleCategory> CATEGORIES = new LinkedHashMap<>();
    private static final List<Entry> RESOLVERS = new ArrayList<>();
    private static long nextOrder;

    private NeutralRoleCategoryApi() {
    }

    public static synchronized void registerCategory(@NotNull NeutralRoleCategory category) {
        Objects.requireNonNull(category, "category");
        CATEGORIES.put(category.id(), category);
    }

    public static synchronized void registerRole(@NotNull Identifier registrationId,
                                                 int priority,
                                                 @NotNull Role role,
                                                 @NotNull Identifier categoryId) {
        Objects.requireNonNull(role, "role");
        registerResolver(registrationId, priority,
                candidate -> candidate == role ? categoryId : null);
    }

    public static synchronized void registerResolver(@NotNull Identifier registrationId,
                                                     int priority,
                                                     @NotNull Resolver resolver) {
        Objects.requireNonNull(registrationId, "registrationId");
        Objects.requireNonNull(resolver, "resolver");
        RESOLVERS.removeIf(entry -> entry.id().equals(registrationId));
        RESOLVERS.add(new Entry(registrationId, priority, nextOrder++, resolver));
        RESOLVERS.sort(ENTRY_COMPARATOR);
    }

    public static synchronized @Nullable NeutralRoleCategory getCategory(@NotNull Identifier categoryId) {
        return CATEGORIES.get(categoryId);
    }

    public static @Nullable NeutralRoleCategory resolveCategory(@Nullable Role role) {
        if (role == null || role.getFaction() != Faction.NEUTRAL) {
            return null;
        }
        List<Entry> entries;
        synchronized (NeutralRoleCategoryApi.class) {
            entries = List.copyOf(RESOLVERS);
        }
        for (Entry entry : entries) {
            Identifier categoryId = entry.resolver().resolve(role);
            if (categoryId == null) {
                continue;
            }
            synchronized (NeutralRoleCategoryApi.class) {
                NeutralRoleCategory category = CATEGORIES.get(categoryId);
                if (category != null) {
                    return category;
                }
            }
        }
        return null;
    }

    public static boolean isInCategory(@Nullable Role role, @NotNull Identifier categoryId) {
        NeutralRoleCategory category = resolveCategory(role);
        return category != null && category.id().equals(categoryId);
    }

    public static boolean isKillerNeutral(@Nullable Role role) {
        return isInCategory(role, NeutralRoleCategories.KILLER_NEUTRAL_ID);
    }

    public static boolean isOrdinaryNeutral(@Nullable Role role) {
        return isInCategory(role, NeutralRoleCategories.ORDINARY_NEUTRAL_ID);
    }

    public static boolean isIndependentNeutral(@Nullable Role role) {
        return isInCategory(role, NeutralRoleCategories.INDEPENDENT_NEUTRAL_ID);
    }

    @FunctionalInterface
    public interface Resolver {
        @Nullable Identifier resolve(@NotNull Role role);
    }

    private record Entry(@NotNull Identifier id, int priority, long order, @NotNull Resolver resolver) {
    }
}
