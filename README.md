# Changed Projects Task Plugin
A Gradle plugin to run a user defined task on changed projects (modules) and their dependent projects (modules) based on git changes.
This is based on either the HEAD commit, a specific commit id or even a commit range.

## Installation
Recommended way is to apply the plugin to the root `build.gradle` in the `plugins` block
```groovy
plugins {
    id 'io.github.crimix.changed-projects-task' version 'VERSION'
}
```
and then configure the plugin using the following block still in the root `build.gradle` 
```groovy
changedProjectsTask {
    taskToRun = "test" //One of the main use cases of the plugin
    alwaysRunProject = [
            ":some-other-project"
    ]
    affectsAllRegex = [
            ~'build.gradle$' //Changes to the root build.gradle affects all projects
    ]
    ignoredRegex = [
            ~'^.*([.]css|[.]html)$' //Ignore changes to front-end files
    ]
}
```

## Configuration
As seen above, there are a few different configuration options available

| **Option**            | **Explanation**                                                                                                                                                                                                                                                                                                                                                                              |
|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `debugLogging`        | Is default false and can be left out.<br/>If true will print details during plugin configuration and execution.                                                                                                                                                                                                                                                                              |
| `taskToRun`           | A name of a task to run on changed projects.                                                                                                                                                                                                                                                                                                                                                 |
| `alwaysRunProject`    | A set of string for project paths starting with `:` that will be run always when there is a not ignored changed file.                                                                                                                                                                                                                                                                        |
| `affectsAllRegex`     | A set of regexes that if any file matches will cause the `taskToRun` to be executed for all projects.                                                                                                                                                                                                                                                                                        |
| `ignoredRegex`        | A set of regexes for files that are ignored when evaluating if any project has changed.                                                                                                                                                                                                                                                                                                      |
| `changedProjectsMode` | A string that denotes which mode the plugin is running in, either `ONLY_DIRECTLY` or `INCLUDE_DEPENDENTS`.<br/><br/>`INCLUDE_DEPENDENTS` is the default and causes the `taskToRun` to be executed for project that are changed and projects that depends on those changed.<br/><br/>`ONLY_DIRECTLY` causes the `taskToRun` to only be executed for projects that are changed and only those. |

## Usage
To use the added `runTaskForChangedProjects` from this plugin you need to run it with a few parameters.
The minimum required is `-PchangedProjectsTask.run` which enables the plugin to run.
Then there are two other optional parameters `-PchangedProjectsTask.commit` and `-PchangedProjectsTask.prevCommit`.

- `-PchangedProjectsTask.commit` is to configure which commit to use in the git diff.
  - If this is specified with `-PchangedProjectsTask.prevCommit` it creates a range to use in diff.   
  By calling the following `git diff --name-only prevCommit~ commit`.
  - If it is specified with `-PchangedProjectsTask.prevCommit`, it uses the following command instead  
  `git diff --name-only commit~ commit`
- `-PchangedProjectsTask.prevCommit` is to configure which previous commit to use in the git diff.   
This cannot be used without also using `-PchangedProjectsTask.commit`

If neither `-PchangedProjectsTask.commit` and `-PchangedProjectsTask.prevCommit` is specified when running the `runTaskForChangedProjects`
then it simply defaults to using `HEAD` by calling this command instead `git diff --name-only HEAD~ HEAD`.

## Example for evaluating the plugin
This is a basic example you can use to evaluate the plugin on your project, apply the following to your own root `build.gradle`.

```groovy
plugins {
    id 'io.github.crimix.changed-projects-task' version 'VERSION'
}

allprojects {
    task print {
        doLast {
            println ">> " + project.path
        }
    }
}

changedProjectsTask {
    taskToRun = "print"
}
```
Then run the following Gradle command line   
`runTaskForChangedProjects -PchangedProjectsTask.run`

This example will print the path of all the projects that is affected by some change and write `Task x:print SKIPPED` for those not affected.  
You can use this to test how the plugin works and also set up the configuration of the plugin using real-world changes in your project.  
This way to can skip running time-consuming task like test when you are just configuring the plugin.

## Why did I make this
I have for at least a month been looking for a plugin or way to do this in Gradle.
I have found a few interesting articles and plugins/code snippets, but none that worked out-of-the-box or suited my needs. 

Thus, I began to write it using plain Groovy and Gradle, but when I was nearly done I stopped and thought for a moment, 
because all the embedded filtering and logic could be removed and put into a configuration instead, such that others could use it. 
Leading to this plugin being created
