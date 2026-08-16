package org.agmas.harpymodloader.api.assignment;

@FunctionalInterface
public interface RoleAssignmentPhaseHandler {
    void handle(RoleAssignmentPhaseContext context);
}
