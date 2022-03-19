package io.github.crimix.changedprojectstask;

import io.github.crimix.changedprojectstask.configuration.ChangedProjectsConfiguration;
import io.github.crimix.changedprojectstask.extensions.Extensions;
import io.github.crimix.changedprojectstask.task.ChangedProjectsTask;
import lombok.experimental.ExtensionMethod;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

@ExtensionMethod(Extensions.class)
public class ChangedProjectsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (!project.isRootProject()) {
            throw new IllegalArgumentException(String.format("Must be applied to root project %s, but was found on %s instead.", project.getRootProject(), project.getName()));
        }
        ChangedProjectsConfiguration extension = project.getExtensions().create("changedProjectsTask", ChangedProjectsConfiguration.class);
        Task task = project.getTasks().register("runTaskForChangedProjects").get();
        if (project.hasBeenEnabled()) {
            ChangedProjectsTask.configureAndRun(project, task, extension);
        }
    }
}
