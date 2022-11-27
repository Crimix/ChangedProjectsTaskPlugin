package io.github.crimix.changedprojectstask.task;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsChoice;
import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.providers.ChangedFilesProvider;
import io.github.crimix.changedprojectstask.providers.ProjectDependencyProvider;
import io.github.crimix.changedprojectstask.utils.LoggingOutputStream;
import lombok.SneakyThrows;
import lombok.experimental.ExtensionMethod;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ExtensionMethod(Extensions.class)
public class ChangedProjectsTask {

    private final Project project;
    private final Task task;
    private final ChangedProjectsConfiguration extension;

    private boolean affectsAll = false;
    private Set<Project> affectedProjects = new HashSet<>();
    private Set<Project> alwaysRunProjects = new HashSet<>();
    private Set<Project> neverRunProjects = new HashSet<>();

    private ChangedProjectsTask(Project project, Task task, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.task = task;
        this.extension = extension;
    }

    public static void configureAndRun(Project project, Task task, ChangedProjectsConfiguration extension) {
        ChangedProjectsTask changedProjectsTask = new ChangedProjectsTask(project, task, extension);
        if (!project.shouldUseCommandLine()){
            changedProjectsTask.configureBeforeEvaluate();
        }
        project.getGradle().projectsEvaluated(g -> changedProjectsTask.afterEvaluate());

    }

    private void configureBeforeEvaluate() {
        for (Project project : project.getAllprojects()) {
            configureProject(project);
        }
    }

    private void afterEvaluate() {
        configureAfterAllEvaluate();
        if (project.shouldUseCommandLine()) {
            commandLineRunProjects();
        }
    }

    private void commandLineRunProjects() {
        for (Project project : project.getAllprojects()) {
            if (shouldProjectRun(project)) {
                runCommandLineOnProject(project);
            }
        }
    }

    private void configureProject(Project project) {
        project.afterEvaluate(p -> {
            String path = getPathToTask(p);
            task.dependsOn(path);
            Task otherTask = p.getTasks().findByPath(path);
            if (otherTask != null) {
                otherTask.onlyIf(t -> shouldProjectRun(p));
                //configureTaskDependenciesRecursively(otherTask, t -> shouldProjectRun(p));
            }
        });
    }

    /*
    private void configureTaskDependenciesRecursively(Task task, Spec<? super Task> var1){
        for(Task dependsOn : task.getTaskDependencies().getDependencies(task)) {
            dependsOn.onlyIf(var1);
            configureTaskDependenciesRecursively(dependsOn, var1);
        }
    }
    */

    private boolean shouldProjectRun(Project p) {
        return !neverRunProjects.contains(p) && (affectsAll || affectedProjects.contains(p) || alwaysRunProjects.contains(p));
    }

    private void configureAfterAllEvaluate() {
        extension.validate(project);
        if (hasBeenEnabled()) {
            extension.print(project, getLogger());
            Project project = getRootProject();
            ChangedFilesProvider changedFilesProvider = new ChangedFilesProvider(project, extension);
            changedFilesProvider.printDebug(getLogger());

            if (changedFilesProvider.getChangedFiles().isEmpty() && !changedFilesProvider.isAllProjectsAffected()) {
                return; //If there are no changes, and we are not forced to run all projects, just skip the rest of the configuration
            }

            configureAlwaysAndNeverRun(project);

            // If we have already determined that we should run all, then no need to spend more time on finding the specific projects
            if (changedFilesProvider.isAllProjectsAffected()) {
                affectsAll = true;
            } else {
                ProjectDependencyProvider projectDependencyProvider = new ProjectDependencyProvider(project, extension);
                projectDependencyProvider.printDebug(getLogger());

                Set<Project> directlyAffectedProjects = evaluateDirectAffectedProjects(changedFilesProvider, projectDependencyProvider);

                if (extension.shouldLog()) {
                    getLogger().lifecycle("Directly affected projects: {}", directlyAffectedProjects);
                }

                Set<Project> dependentAffectedProjects = new HashSet<>();
                if (ChangedProjectsChoice.INCLUDE_DEPENDENTS == extension.getPluginMode()) {
                    dependentAffectedProjects.addAll(projectDependencyProvider.getAffectedDependentProjects(directlyAffectedProjects));
                    if (extension.shouldLog()) {
                        getLogger().lifecycle("Dependent affected Projects: {}", dependentAffectedProjects);
                    }
                }

                affectedProjects = Stream.concat(directlyAffectedProjects.stream(), dependentAffectedProjects.stream())
                        .collect(Collectors.toSet());
            }
        }
    }

    @SneakyThrows
    private void runCommandLineOnProject(Project affected) {
        String commandLine = String.format("%s %s %s", getGradleWrapper(), getPathToTask(affected), project.getCommandLineArgs());
        getLogger().lifecycle("Running {}", commandLine);
        LoggingOutputStream stdout = new LoggingOutputStream(project.getLogger()::lifecycle);
        LoggingOutputStream stderr = new LoggingOutputStream(project.getLogger()::error);
        //We use Apache Commons Exec because we do not want to re-invent the wheel as ProcessBuilder hangs if the output or error buffer is full
        DefaultExecutor exec = new DefaultExecutor();
        exec.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        exec.setWorkingDirectory(project.getRootProject().getProjectDir());
        int exitValue = exec.execute(CommandLine.parse(commandLine));

        if (exitValue != 0) {
            throw new IllegalStateException("Executing command failed");
        }
    }

    private String getGradleWrapper() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return "gradlew.bat";
        } else {
            return "./gradlew";
        }
    }

    private Set<Project> evaluateDirectAffectedProjects(ChangedFilesProvider changedFilesProvider, ProjectDependencyProvider projectDependencyProvider) {
        return changedFilesProvider.getChangedFiles().stream()
                .map(projectDependencyProvider::getChangedProject)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void configureAlwaysAndNeverRun(Project project) {
        Set<String> alwaysRunPath = extension.getAlwaysRunProject().getOrElse(Collections.emptySet());
        alwaysRunProjects = project.getAllprojects().stream()
                .filter(p -> alwaysRunPath.contains(p.getPath()))
                .collect(Collectors.toSet());
        if (extension.shouldLog()) {
            getLogger().lifecycle("Always run projects: {}", alwaysRunProjects);
        }

        Set<String> neverRunPath = extension.getNeverRunProject().getOrElse(Collections.emptySet());
        neverRunProjects = project.getAllprojects().stream()
                .filter(p -> neverRunPath.contains(p.getPath()))
                .collect(Collectors.toSet());
        if (extension.shouldLog()) {
            getLogger().lifecycle("Never run projects: {}", neverRunProjects);
        }
    }

    private Project getRootProject() {
        return project.getRootProject();
    }

    private boolean hasBeenEnabled() {
        return project.hasBeenEnabled();
    }

    private Logger getLogger() {
        return project.getLogger();
    }

    private String getPathToTask(Project project) {
        String taskToRun = project.getTaskToRun(extension);
        if (project.isRootProject()) {
            return String.format(":%s", taskToRun);
        } else {
            return String.format("%s:%s", project.getPath(), taskToRun);
        }
    }

}
