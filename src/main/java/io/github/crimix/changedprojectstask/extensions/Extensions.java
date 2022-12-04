package io.github.crimix.changedprojectstask.extensions;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsChoice;
import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.utils.GitDiffMode;
import lombok.SneakyThrows;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static io.github.crimix.changedprojectstask.utils.Properties.*;

/**
 * Class the contains the Lombok extension methods
 */
public class Extensions {

    /**
     * Returns whether the project is the root project.
     * @return true if the project is the root project
     */
    public static boolean isRootProject(Project project) {
        return project.equals(project.getRootProject());
    }

    /**
     * Gets the name of the project's directory
     * @return the name of the project's directory
     */
    public static String getProjectDirName(Project project) {
        return project.getProjectDir().getName();
    }

    /**
     * Returns whether the plugin's task is allowed to run and configure.
     * @return true if the plugin's task is allowed to run and configure
     */
    public static boolean hasBeenEnabled(Project project) {
        return project.getRootProject().hasProperty(ENABLE) || project.getRootProject().hasProperty(ENABLE_COMMANDLINE);
    }


    /**
     * Gets the task to run, this is either the override from CLI arugment of the default configured task.
     * @return task to run
     */
    public static String getTaskToRun(Project project, ChangedProjectsConfiguration configuration) {
        return Optional.of(project)
                .map(Project::getRootProject)
                .map(p -> p.findProperty(TASK_TO_RUN))
                .map(String.class::cast)
                .orElseGet(() -> configuration.getTaskToRun().getOrNull());
    }

    /**
     * Gets the configured commit id
     * @return either an optional with the commit id or an empty optional if it has not been configured
     */
    public static Optional<String> getCommitId(Project project) {
        return Optional.of(project)
                .map(Project::getRootProject)
                .map(p -> p.findProperty(CURRENT_COMMIT))
                .map(String.class::cast);
    }


    /**
     * Returns if the task to runs should be invoked using the commandline instead of using the task onlyIf approach.
     * @return true if the task should be invoked using the commandline
     */
    public static boolean shouldUseCommandLine(Project project) {
        return project.getRootProject().hasProperty(ENABLE_COMMANDLINE);
    }

    /**
     * Gets the commandline arguments specified for use when invoking the task to run using the commandline.
     * @return the commandline arguments as a string
     */
    public static String getCommandLineArgs(Project project) {
        return Optional.of(project)
                .map(Project::getRootProject)
                .map(p -> p.findProperty(COMMANDLINE_ARGS))
                .map(String.class::cast)
                .orElse("");
    }

    /**
     * Gets the configured previous commit id
     * @return either an optional with the previous commit id or an empty optional if it has not been configured
     */
    public static Optional<String> getPreviousCommitId(Project project) {
        return Optional.of(project)
                .map(Project::getRootProject)
                .map(p -> p.findProperty(PREVIOUS_COMMIT))
                .map(String.class::cast);
    }

    /**
     * Gets the configured git commit compare mode if specified.
     * Defaults to {@link GitDiffMode#COMMIT} if none specified.
     * @return the configured git compare mode or {@link GitDiffMode#COMMIT}
     */
    public static GitDiffMode getCommitCompareMode(Project project) {
        return Optional.of(project)
                .map(Project::getRootProject)
                .map(p -> p.findProperty(COMMIT_MODE))
                .map(String.class::cast)
                .map(GitDiffMode::getMode)
                .orElse(GitDiffMode.COMMIT);
    }

    /**
     * Finds the git root for the project.
     * @return a file that represents the git root of the project.
     */
    public static File getGitRootDir(Project project) {
        File currentDir = project.getRootProject().getProjectDir();

        //Keep going until we either hit a .git dir or the root of the file system on either Windows or Linux
        while (currentDir != null && !currentDir.getPath().equals("/")) {
            if (new File(String.format("%s/.git", currentDir.getPath())).exists()) {
                return currentDir;
            }
            currentDir = currentDir.getParentFile();
        }

        return null;
    }

    /**
     * Gets the canonical path to the project.
     * @return the canonical path to the project.
     */
    @SneakyThrows(IOException.class)
    public static Path getCanonicalProjectPath(Project project) {
        return project.getProjectDir().getCanonicalFile().toPath();
    }

    /**
     * Gets the canonical path's string length to the project.
     * @return the canonical path's string length to the project.
     */
    @SneakyThrows(IOException.class)
    public static int getCanonicalProjectPathStringLength(Project project) {
        return project.getProjectDir().getCanonicalFile().toPath().toString().length();
    }

    /**
     * Gets the canonical path to the file.
     * @return the canonical path to the file.
     */
    @SneakyThrows(IOException.class)
    public static Path getCanonicalFilePath(File file) {
        return file.getCanonicalFile().toPath();
    }

    /**
     * Runs validation on the configuration.
     */
    public static void validate(ChangedProjectsConfiguration configuration, Project root) {
        String taskToRun = getTaskToRun(root, configuration);
        if (taskToRun == null || taskToRun.isEmpty()) {
            throw new IllegalArgumentException("changedProjectsTask: taskToRun is required");
        } else if (taskToRun.startsWith(":")) {
            throw new IllegalArgumentException("changedProjectsTask: taskToRun should not start with :");
        }
        Set<String> projectsAlwaysRun = configuration.getAlwaysRunProject().getOrElse(Collections.emptySet());
        for (String project : projectsAlwaysRun) {
            if (!project.startsWith(":")) {
                throw new IllegalArgumentException(String.format("changedProjectsTask: alwaysRunProject %s must start with :", project));
            }
        }

        configuration.getAffectsAllRegex().getOrElse(Collections.emptySet()); //Gradle will throw if the type does not match
        configuration.getIgnoredRegex().getOrElse(Collections.emptySet()); //Gradle will throw if the type does not match
        String mode = configuration.getChangedProjectsMode().getOrElse(ChangedProjectsChoice.INCLUDE_DEPENDENTS.name());
        try {
            ChangedProjectsChoice.valueOf(mode);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException(String.format("changedProjectsTask: ChangedProjectsMode must be either %s or %s ", ChangedProjectsChoice.ONLY_DIRECTLY.name(), ChangedProjectsChoice.INCLUDE_DEPENDENTS.name()));
        }
    }

    /**
     * Gets the plugin's configured mode
     * @return the mode the plugin is configured to use
     */
    public static ChangedProjectsChoice getPluginMode(ChangedProjectsConfiguration configuration) {
        return ChangedProjectsChoice.valueOf(configuration.getChangedProjectsMode().getOrElse(ChangedProjectsChoice.INCLUDE_DEPENDENTS.name()));
    }

    /**
     * Prints the configuration.
     * @param logger the logger to print the configuration to.
     */
    public static void print(ChangedProjectsConfiguration configuration, Project project, Logger logger) {
        if (shouldLog(configuration)) {
            logger.lifecycle("Printing configuration");
            logger.lifecycle("Task to run {}", getTaskToRun(project, configuration));
            logger.lifecycle("Always run project {}", configuration.getAlwaysRunProject().getOrElse(Collections.emptySet()));
            logger.lifecycle("Never run project {}", configuration.getNeverRunProject().getOrElse(Collections.emptySet()));
            logger.lifecycle("Affects all regex {}", configuration.getAffectsAllRegex().getOrElse(Collections.emptySet()));
            logger.lifecycle("Ignored regex {}", configuration.getIgnoredRegex().getOrElse(Collections.emptySet()));
            logger.lifecycle("Mode {}", getPluginMode(configuration));
            logger.lifecycle("");
        }
    }

    /**
     * Returns whether the plugin should log debug information to the Gradle log
     * @return true if the plugin should debug log
     */
    public static boolean shouldLog(ChangedProjectsConfiguration configuration) {
        return configuration.getDebugLogging().getOrElse(false);
    }
}
