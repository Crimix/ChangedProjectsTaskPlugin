package io.github.crimix.changedprojectstask.providers;

import io.github.crimix.changedprojectstask.utils.GitDiffMode;
import org.assertj.core.api.Assertions;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

public class GitCommandProviderTest {

    private static final String CURR = "curr";
    private static final String PREV = "prev";
    private static final String NOT_SET = null;

    public static Stream<Arguments> provideStringsForIsBlank() {
        return Stream.of(
                Arguments.of(NOT_SET, NOT_SET, GitDiffMode.COMMIT, false, "git diff --name-only HEAD~ HEAD"),
                Arguments.of(CURR, NOT_SET, GitDiffMode.COMMIT, false, "git diff --name-only curr~ curr"),
                Arguments.of(CURR, PREV, GitDiffMode.COMMIT, false, "git diff --name-only prev~ curr"),
                Arguments.of(NOT_SET, PREV, GitDiffMode.COMMIT, true, "[COMMIT] When using changedProjectsTask.prevCommit then changedProjectsTask.commit must also be specified"),

                Arguments.of(NOT_SET, NOT_SET, GitDiffMode.BRANCH, true, "[BRANCH] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, NOT_SET, GitDiffMode.BRANCH, true, "[BRANCH] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, PREV, GitDiffMode.BRANCH, false, "git diff --name-only prev curr"),
                Arguments.of(NOT_SET, PREV, GitDiffMode.BRANCH, false, "git diff --name-only prev HEAD"),

                Arguments.of(NOT_SET, NOT_SET, GitDiffMode.BRANCH_TWO_DOT, true, "[BRANCH_TWO_DOT] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, NOT_SET, GitDiffMode.BRANCH_TWO_DOT, true, "[BRANCH_TWO_DOT] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, PREV, GitDiffMode.BRANCH_TWO_DOT, false, "git diff --name-only prev..curr"),
                Arguments.of(NOT_SET, PREV, GitDiffMode.BRANCH_TWO_DOT, false, "git diff --name-only prev.."),

                Arguments.of(NOT_SET, NOT_SET, GitDiffMode.BRANCH_THREE_DOT, true, "[BRANCH_THREE_DOT] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, NOT_SET, GitDiffMode.BRANCH_THREE_DOT, true, "[BRANCH_THREE_DOT] changedProjectsTask.prevCommit must always be specified"),
                Arguments.of(CURR, PREV, GitDiffMode.BRANCH_THREE_DOT, false, "git diff --name-only prev...curr"),
                Arguments.of(NOT_SET, PREV, GitDiffMode.BRANCH_THREE_DOT, false, "git diff --name-only prev...")
        );
    }

    @ParameterizedTest
    @MethodSource("provideStringsForIsBlank")
    public void test(String current, String previous, GitDiffMode mode, boolean exception, String expected) {
        Project project = ProjectBuilder.builder()
                .withName("root")
                .build();

        GitCommandProvider provider = new GitCommandProvider(project);
        if (!exception) {
            String actual = provider.evaluate(mode, Optional.ofNullable(current), Optional.ofNullable(previous));
            Assertions.assertThat(actual)
                    .isEqualTo(expected);
        } else {
            Assertions.assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> provider.evaluate(mode, Optional.ofNullable(current), Optional.ofNullable(previous)))
                    .withMessage(expected);
        }

    }
}