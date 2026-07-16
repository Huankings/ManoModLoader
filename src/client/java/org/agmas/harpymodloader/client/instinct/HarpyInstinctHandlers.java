package org.agmas.harpymodloader.client.instinct;

import org.agmas.harpymodloader.client.instinct.observer.ObserverRoleColorInstinctHandler;

public final class HarpyInstinctHandlers {
    private HarpyInstinctHandlers() {
    }

    public static void register() {
        ObserverRoleColorInstinctHandler.register();
    }
}
