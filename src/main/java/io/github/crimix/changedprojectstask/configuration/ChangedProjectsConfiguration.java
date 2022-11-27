package io.github.crimix.changedprojectstask.configuration;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import java.util.regex.Pattern;

/**
 * The configuration that the user of the plugin can change to affect the behavior of the plugin
 */
public interface ChangedProjectsConfiguration {

    /**
     * If the plugin should log stuff like changed files and identified project dependencies.
     * This is mostly used to debug if the plugin does not works as expected on a project.
     * @return whether the plugin to do debug logging
     */
    Property<Boolean> getDebugLogging();

    /**
     * The task to run on the changed projects and those affected by the change (If chosen).
     * @return the task name
     */
    Property<String> getTaskToRun();

    /**
     * The projects to execute the task on when there is a not ignored change.
     * @return a list of project paths
     */
    SetProperty<String> getAlwaysRunProject();

    /**
     * The projects to never execute the task on even when it has changed.
     * @return a list of project paths
     */
    SetProperty<String> getNeverRunProject();

    /**
     * The list of regexes that filters the not ignored changes to see if any change has been marked to affect all projects
     * and thus needs to run the task on the root project instead.
     * @return a list of compiled patterns
     */
    SetProperty<Pattern> getAffectsAllRegex();

    /**
     * The list of regexes that filters the changes.
     * THis can be used to as an example ignored specific directories or file extensions.
     * @return a list of compiled patterns
     */
    SetProperty<Pattern> getIgnoredRegex();

    /**
     * The mode in which the plugin should work.
     * Either {@link ChangedProjectsChoice#ONLY_DIRECTLY} which means the task is only run for projects affected by changes files directly
     * or {@link ChangedProjectsChoice#INCLUDE_DEPENDENTS} which means the task is run on directly changed projects and projects dependent on those projects
     * Defaults to {@link ChangedProjectsChoice#INCLUDE_DEPENDENTS}
     * @return which mode the plugin is in
     */
    Property<String> getChangedProjectsMode();

}
