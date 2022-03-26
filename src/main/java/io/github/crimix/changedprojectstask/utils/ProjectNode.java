package io.github.crimix.changedprojectstask.utils;

import io.github.crimix.changedprojectstask.extensions.Extensions;
import lombok.experimental.ExtensionMethod;
import org.gradle.api.Project;

import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@ExtensionMethod(Extensions.class)
public class ProjectNode {

    private final Project project;
    private final Map<String, ProjectNode> childNodes;

    public ProjectNode(Project project) {
        this.project = project;
        this.childNodes = project.getChildProjects().values().stream()
                .collect(Collectors.toMap(p -> p.getProjectDirName(), ProjectNode::new));
    }

    /**
     * Gets thr project.
     * @return the project of the node
     */
    public Project getProject() {
        return project;
    }

    /**
     * Finds a child project using its directory name as the path
     * @param path the directory name for the child project
     * @return either the child project or an empty optional if no child project is found
     */
    public Optional<ProjectNode> getProjectNodeFromPath(String path) {
        return Optional.ofNullable(childNodes.get(path));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProjectNode.class.getSimpleName() + "[", "]")
                .add("project=" + project)
                .add("childNodes=" + childNodes)
                .toString();
    }
}
