package io.github.crimix.changedprojectstask.utils;

/**
 * Configurable properies that can be used in gralde using the -P prefix
 * Like -PchangedProjectsTask.enable
 */
public class Properties {
    public static final String ENABLE = "changedProjectsTask.run";
    public static final String CURRENT_COMMIT = "changedProjectsTask.commit";
    public static final String PREVIOUS_COMMIT = "changedProjectsTask.prevCommit";
}
