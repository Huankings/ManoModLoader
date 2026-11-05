package org.agmas.harpymodloader.client.instinct;

import org.agmas.harpymodloader.client.instinct.observer.ObserverRoleColorInstinctHandler;
import org.agmas.harpymodloader.client.instinct.killer.KillerNeutralInstinctHandler;

public final class HarpyInstinctHandlers {
    private HarpyInstinctHandlers() {
    }

    public static void register() {
        KillerNeutralInstinctHandler.register();
        ObserverRoleColorInstinctHandler.register();
    }
}
