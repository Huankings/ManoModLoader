package org.agmas.harpymodloader.api.assignment;

@FunctionalInterface
public interface ModifierAssignmentRule {
    AssignmentDecision test(ModifierAssignmentContext context);
}
