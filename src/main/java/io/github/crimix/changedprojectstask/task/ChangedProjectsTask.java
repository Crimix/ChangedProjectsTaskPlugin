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
    private final Task task;
    private final ChangedProjectsConfiguration extension;

    private boolean affectsAll = false;
    private Set<Project> affectedProjects = new HashSet<>();
    private Set<Project> alwaysRunProjects = new HashSet<>();

    private ChangedProjectsTask(Project project, Task task, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.task = task;
        this.extension = extension;
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
            String path = getPathToTask(p);
            task.dependsOn(path);
            Task otherTask = p.getTasks().findByPath(path);
            if (otherTask != null) {
                otherTask.onlyIf(t -> affectsAll || affectedProjects.contains(p) || alwaysRunProjects.contains(p));
            }
        });
    }

    private void configureAfterAllEvaluate() {
        extension.validate();
        if (hasBeenEnabled()) {
            extension.print(getLogger());
            Project project = getRootProject();
            ChangedFilesProvider changedFilesProvider = new ChangedFilesProvider(project, extension);
            changedFilesProvider.printDebug(getLogger());

            if (changedFilesProvider.getChangedFiles().isEmpty() && !changedFilesProvider.isAllProjectsAffected()) {
                return; //If there are no changes, and we are not forced to run all projects, just skip the rest of the configuration
            }

            configureAlwaysRun(project);

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

    private void configureAlwaysRun(Project project) {
        Set<String> alwaysRunPath = extension.getAlwaysRunProject().getOrElse(Collections.emptySet());
        alwaysRunProjects = project.getAllprojects().stream()
                .filter(p -> alwaysRunPath.contains(p.getPath()))
                .collect(Collectors.toSet());
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
        String taskToRun = extension.getTaskToRun().getOrNull();
        return String.format("%s:%s", project.getPath(), taskToRun);
    }

}
