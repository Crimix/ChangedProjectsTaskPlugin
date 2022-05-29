package io.github.crimix.changedprojectstask.configuration;

/**
 * The two different modes that the plugin can be used
 */
public enum ChangedProjectsChoice {
    /**
     * Only execute task on those modules that are directly affected
     */
    ONLY_DIRECTLY,

    /**
     * Execute task on all modules in the dependency tree that are affected
     */
    INCLUDE_DEPENDENTS
}
