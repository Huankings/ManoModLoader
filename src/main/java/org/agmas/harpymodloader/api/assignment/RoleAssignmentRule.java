package org.agmas.harpymodloader.api.assignment;

@FunctionalInterface
public interface RoleAssignmentRule {
    AssignmentDecision test(RoleAssignmentContext context);
}
