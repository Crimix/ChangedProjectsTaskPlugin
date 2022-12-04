package io.github.crimix.changedprojectstask.utils;

/**
 * Configurable properies that can be used in gralde using the -P prefix
 * Like -PchangedProjectsTask.enable
 */
public class Properties {
    private static final String PREFIX = "changedProjectsTask.";

    public static final String ENABLE = PREFIX + "run";
    public static final String ENABLE_COMMANDLINE = PREFIX + "runCommandLine";
    public static final String CURRENT_COMMIT = PREFIX + "commit";
    public static final String PREVIOUS_COMMIT = PREFIX + "prevCommit";
    public static final String COMMIT_MODE = PREFIX + "compareMode";
    public static final String TASK_TO_RUN = PREFIX + "taskToRun";
    public static final String COMMANDLINE_ARGS = PREFIX + "commandLineArgs";
}
