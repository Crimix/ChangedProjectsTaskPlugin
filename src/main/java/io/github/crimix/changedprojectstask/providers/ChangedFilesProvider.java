package io.github.crimix.changedprojectstask.providers;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.utils.CollectingOutputStream;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ExtensionMethod(Extensions.class)
public class ChangedFilesProvider {

    private final Project project;
    private final ChangedProjectsConfiguration extension;
    private final GitCommandProvider gitCommandProvider;
    private final List<File> filteredChanges;
    private final boolean affectsAllProjects;

    public ChangedFilesProvider(Project project, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.extension = extension;
        this.gitCommandProvider = new GitCommandProvider(project);
        List<String> gitFilteredChanges = initFilteredChanges();
        this.filteredChanges = initFilteredChangedFiles(gitFilteredChanges);
        this.affectsAllProjects = initAffectsAllProjects(gitFilteredChanges);
    }

    @SneakyThrows
    private List<String> initFilteredChanges() {
        File gitRoot = project.getGitRootDir();
        if (gitRoot == null) {
            throw new IllegalStateException("The project does not have a git root");
        }

        CollectingOutputStream stdout = new CollectingOutputStream();
        CollectingOutputStream stderr = new CollectingOutputStream();
        //We use Apache Commons Exec because we do not want to re-invent the wheel as ProcessBuilder hangs if the output or error buffer is full
        DefaultExecutor exec = new DefaultExecutor();
        exec.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        exec.setWorkingDirectory(gitRoot);
        exec.execute(CommandLine.parse(gitCommandProvider.getGitDiffCommand()));

        if (stderr.isNotEmpty()) {
            if (containsErrors(stderr)) {
                throw new IllegalStateException(String.format("Failed to run git diff because of \n%s", stderr));
            } else {
                if (project.getLogger().isWarnEnabled()) {
                    project.getLogger().warn(stderr.toString());
                }
            }
        }

        if (stdout.isEmpty()) {
            throw new IllegalStateException("Git diff returned no results this must be a mistake");
        }

        //Create a single predicate from the ignored regexes such that we can use a simple filter
        Predicate<String> filter = extension.getIgnoredRegex()
                .getOrElse(Collections.emptySet())
                .stream()
                .map(Pattern::asMatchPredicate)
                .reduce(Predicate::or)
                .orElse(x -> false);

        //Filter and return the list
        return stdout.getLines().stream()
                .filter(Predicate.not(filter))
                .collect(Collectors.toList());
    }

    private boolean containsErrors(CollectingOutputStream stderr) {
        return stderr.getLines().stream().anyMatch(line -> line.startsWith("error:"));
    }

    private boolean initAffectsAllProjects(List<String> gitFilteredChanges) {
        //Create a single predicate from the affects all projects regexes such that we can use a simple filter
        Predicate<String> filter = extension.getAffectsAllRegex()
                .getOrElse(Collections.emptySet())
                .stream()
                .map(Pattern::asMatchPredicate)
                .reduce(Predicate::or)
                .orElse(x -> false);

        return gitFilteredChanges.stream()
                .anyMatch(filter);
    }

    private List<File> initFilteredChangedFiles(List<String> gitFilteredChanges) {
        File gitRoot = project.getGitRootDir();
        if (gitRoot == null) {
            throw new IllegalStateException("The project does not have a git root");
        }

        return gitFilteredChanges.stream()
                .map(s -> new File(gitRoot, s))
                .collect(Collectors.toList());
    }

    /**
     * Gets the filtered changed files
     * @return the filtered changed files
     */
    public List<File> getChangedFiles() {
        return filteredChanges;
    }

    /**
     * Returns whether all projects are affected by the changes specified by the plugin configuration
     * @return true if all projects are affected
     */
    public boolean isAllProjectsAffected() {
        return affectsAllProjects;
    }

    /**
     * Prints debug information if it has been enabled
     * @param logger the logger to print information to
     */
    public void printDebug(Logger logger) {
        if (extension.shouldLog()) {
            logger.lifecycle("Git diff command uses {}", gitCommandProvider.getGitDiffCommand());
            logger.lifecycle("All projects affected? {}", isAllProjectsAffected());
            logger.lifecycle("Changed files:");
            getChangedFiles()
                    .forEach(file -> logger.lifecycle(file.toString()));
            logger.lifecycle("");
        }
    }
}
