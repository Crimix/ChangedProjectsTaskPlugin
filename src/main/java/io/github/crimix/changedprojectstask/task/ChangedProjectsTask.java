package io.github.crimix.changedprojectstask.task;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsChoice;
import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.providers.ChangedFilesProvider;
import io.github.crimix.changedprojectstask.providers.ProjectDependencyProvider;
import lombok.experimental.ExtensionMethod;
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
    private final Task changedProjectsTask;
    private final ChangedProjectsConfiguration extension;

    private boolean affectsAll = false;
    private Set<Project> affectedProjects = new HashSet<>();
    private Set<Project> alwaysRunProjects = new HashSet<>();
    private Set<Project> neverRunProjects = new HashSet<>();

    private ChangedProjectsTask(Project project, Task changedProjectsTask, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.extension = extension;
        this.changedProjectsTask = changedProjectsTask;
    }

    public static void configureAndRun(Project project, Task task, ChangedProjectsConfiguration extension) {
        ChangedProjectsTask changedProjectsTask = new ChangedProjectsTask(project, task, extension);
        changedProjectsTask.configureBeforeEvaluate();
        project.getGradle().projectsEvaluated(g -> changedProjectsTask.configureAfterAllEvaluate());
    }

    private void configureBeforeEvaluate() {
        for (Project project : project.getAllprojects()) {
            configureProject(project);
        }
    }

    private void configureProject(Project project) {
        project.afterEvaluate(p -> {
            Task taskToRun = p.getTasks().findByPath(getPathToTask(p));

            if (taskToRun != null) {
                //make taskToRun run after changedProjectsTask
                changedProjectsTask.dependsOn(taskToRun);
                taskToRun.onlyIf(t -> shouldProjectRun(p));
            }
        });
    }

    private boolean shouldProjectRun(Project p) {
        return !neverRunProjects.contains(p) && (affectsAll || affectedProjects.contains(p) || alwaysRunProjects.contains(p));
    }

    private void configureAfterAllEvaluate() {
        extension.validate(getRootProject());
        if (hasBeenEnabled()) {
            extension.print(getLogger());
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
        String taskToRun = Extensions
                .getTaskToRunParameter(project)
                .orElse(extension.getTaskToRun().getOrNull());

        if (extension.shouldLog()) {
            getLogger().lifecycle("taskToRun: {}", taskToRun);
        }

        if (project.isRootProject()) {
            return String.format(":%s", taskToRun);
        } else {
            return String.format("%s:%s", project.getPath(), taskToRun);
        }
    }

}
