package io.github.crimix.changedprojectstask.providers;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.utils.Pair;
import lombok.experimental.ExtensionMethod;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ExtensionMethod(Extensions.class)
public class ProjectDependencyProvider {

    private final Project project;
    private final ChangedProjectsConfiguration extension;
    private final Map<Project, Set<Project>> projectDependentsMap;

    public ProjectDependencyProvider(Project project, ChangedProjectsConfiguration extension) {
        this.project = project;
        this.extension = extension;
        this.projectDependentsMap = initProjectDependents();
    }

    private Map<Project, Set<Project>> initProjectDependents() {
        //We create a lookup map of projects and the projects that depends on that project once
        //This is to speed up the evaluating dependent changed projects
        //The key of the map is a project that is a direct dependency for the value set
        return project.getSubprojects().stream()
                .map(this::getProjectDependencies)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toSet())));
    }

    private Set<Pair<Project, Project>> getProjectDependencies(Project subproject) {
        //We use a pair, because we want the project that is a dependency together with the project it is a dependency for
        return subproject.getConfigurations().stream()
                .map(Configuration::getDependencies)
                .map(dependencySet -> dependencySet.withType(ProjectDependency.class))
                .flatMap(Set::stream)
                .map(ProjectDependency::getDependencyProject)
                .map(p -> new Pair<>(p, subproject))
                .collect(Collectors.toSet());
    }

    public Project getChangedProject(File file) {
        Path filePath = file.getCanonicalFilePath();
        if (!filePath.startsWith(project.getRootProject().getCanonicalProjectPath())) {
            return null; //We return null here as there is no need to try and find which project it belongs to
        }

        //We find all projects which paths match the start of the files' path.
        //Then we take the project which has the most overlap with the beginning of the file path
        //Else we just use the root project as a fallback
        Project result = project.getAllprojects().stream()
                .filter(doesFilePathStartWithProjectDirPath(file))
                .max(Comparator.comparingInt(Extensions::getCanonicalProjectPathStringLength))
                .orElseGet(getFallback(file));

        if (extension.shouldLog()) {
            project.getLogger().lifecycle("File {} belongs to {}", file, result);
        }

        return result;
    }

    private Predicate<Project> doesFilePathStartWithProjectDirPath(File file) {
        return subproject -> {
            Path subprojectPath = subproject.getCanonicalProjectPath();
            Path filePath = file.getCanonicalFilePath();
            return filePath.startsWith(subprojectPath);
        };
    }

    private Supplier<Project> getFallback(File file) {
        return () -> {
            if (extension.shouldLog()) {
                project.getLogger().lifecycle("USING FALLBACK for file {}", file);
            }
            return project.getRootProject();
        };
    }

    public Set<Project> getAffectedDependentProjects(Set<Project> directlyChangedProjects) {
        //We use this to have a way to break our of recursion when we have already seen that project once
        //This makes it possible to avoid infinite recursion and also speeds up the process
        Set<Project> alreadyVisitedProjects = new HashSet<>();

        return directlyChangedProjects.stream()
                .map(p -> getDependentProjects(p, alreadyVisitedProjects))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private Set<Project> getDependentProjects(Project project, Set<Project> alreadyVisitedProjects) {
        //If we have already visited the project, we can just return empty as we have evaluated dependent projects in another call
        if (alreadyVisitedProjects.contains(project)) {
            return Collections.emptySet();
        }
        Set<Project> result = new HashSet<>(projectDependentsMap.getOrDefault(project, Collections.emptySet()));
        alreadyVisitedProjects.add(project);

        //Continue down the chain until no more new affected projects are found
        Set<Project> dependents = result.stream()
                .map(p -> getDependentProjects(p, alreadyVisitedProjects))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        result.addAll(dependents);
        return result;
    }

    public void printDebug(Logger logger) {
        if (extension.shouldLog()) {
            logger.lifecycle("Printing project dependents map");
            projectDependentsMap.forEach((key, value) -> logger.lifecycle("Project: {} is a direct dependent for the following {}", key, value));
            logger.lifecycle("");
        }
    }
}
