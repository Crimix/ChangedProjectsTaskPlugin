package io.github.crimix.changedprojectstask.utils;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The available modes to use when trying to get the git diff
 */
public enum GitDiffMode {
    COMMIT("commit"),
    BRANCH("branch"),
    BRANCH_TWO_DOT("branchTwoDotted"),
    BRANCH_THREE_DOT("branchThreeDotted");

    private final String commandOption;

    GitDiffMode(String commandOption) {
        this.commandOption = commandOption;
    }

    /**
     * Gets the command line optional name of the mode.
     * @return the command line optional name of the mode
     */
    public String getCommandOption() {
        return commandOption;
    }

    /**
     * Gets the mode from the command line option or throws an exception if the command line option does not match a mode.
     * @param commandOption the command line option
     * @return the mode corresponding to the command line option
     */
    public static GitDiffMode getMode(String commandOption) {
        return Arrays.stream(GitDiffMode.values())
                .filter(e -> e.getCommandOption().equals(commandOption))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(String.format("Unknown compare mode %s available [%s]", commandOption, GitDiffMode.getAvailableOptions())));
    }

    /**
     * Gets the available command line options as a string
     * @return the available command line options as a string
     */
    private static String getAvailableOptions() {
        return Arrays.stream(GitDiffMode.values())
                .map(GitDiffMode::getCommandOption)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
