package io.github.crimix.changedprojectstask.providers;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.utils.CollectingOutputStream;
import io.github.crimix.changedprojectstask.utils.Pair;
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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.crimix.changedprojectstask.utils.Properties.CURRENT_COMMIT;
import static io.github.crimix.changedprojectstask.utils.Properties.PREVIOUS_COMMIT;

@ExtensionMethod(Extensions.class)
public class ChangedFilesProvider {

    // The default if no commit ids have been specified
    private static final String HEAD = "HEAD";

    private final Project project;
    private final ChangedProjectsConfiguration extension;
    private final Pair<String, String> commitIds;
    private final List<File> filteredChanges;
    private final boolean affectsAllProjects;

    public ChangedFilesProvider(Project project, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.extension = extension;
        this.commitIds = initCommitIds();
        List<String> gitFilteredChanges = initFilteredChanges();
        this.filteredChanges = initFilteredChangedFiles(gitFilteredChanges);
        this.affectsAllProjects = initAffectsAllProjects(gitFilteredChanges);
    }

    private Pair<String, String> initCommitIds() {
        Optional<String> currentCommitId = project.getCommitId();
        Optional<String> previousCommitId = project.getPreviousCommitId();

        //If only currentCommitId has been specified then we assume that it is the diff of that specific commit
        if (currentCommitId.isPresent() && previousCommitId.isPresent()) {
          return new Pair<>(currentCommitId.get(), String.format("%s~", previousCommitId.get()));
        } else if (currentCommitId.isPresent()) {
            return new Pair<>(currentCommitId.get(), String.format("%s~", currentCommitId.get()));
        } else if (previousCommitId.isPresent()) {
            throw new IllegalStateException(String.format("When using %s then %s must also be specified", PREVIOUS_COMMIT, CURRENT_COMMIT));
        } else {
            return new Pair<>(HEAD, String.format("%s~", HEAD));
        }
    }

    @SneakyThrows
    private List<String> initFilteredChanges() {
        File gitRoot = project.getGitRootDir();
        if (gitRoot == null) {
            throw new IllegalStateException("The project does not have a git root");
        }

        CollectingOutputStream stdout = new CollectingOutputStream();
        CollectingOutputStream stderr = new CollectingOutputStream();
        DefaultExecutor exec = new DefaultExecutor();
        exec.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        exec.setWorkingDirectory(gitRoot);
        exec.execute(CommandLine.parse(String.format("git diff --name-only %s %s", commitIds.getValue(), commitIds.getKey())));

        if (stderr.isNotEmpty()) {
            throw new IllegalStateException(String.format("Failed to run git diff because of \n%s", stdout));
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
            logger.lifecycle("Git diff command will use {} {}", commitIds.getValue(), commitIds.getKey());
            logger.lifecycle("All projects affected? {}", isAllProjectsAffected());
            logger.lifecycle("Changed files:");
            getChangedFiles()
                    .forEach(file -> logger.lifecycle(file.toString()));
            logger.lifecycle("");
        }
    }
}
