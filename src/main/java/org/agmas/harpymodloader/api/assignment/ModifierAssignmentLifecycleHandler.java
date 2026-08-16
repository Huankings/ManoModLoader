package org.agmas.harpymodloader.api.assignment;

@FunctionalInterface
public interface ModifierAssignmentLifecycleHandler {
    void handle(ModifierAssignmentLifecycleContext context);
}
