package io.github.crimix.changedprojectstask.utils;

import org.apache.commons.exec.LogOutputStream;

import java.util.LinkedList;
import java.util.List;

public class CollectingOutputStream extends LogOutputStream {

    private final List<String> lines = new LinkedList<>();

    @Override
    protected void processLine(String line, int level) {
        lines.add(line);
    }

    /**
     * Gets the lines collected by the output stream.
     * @return the lines collected by the output stream.
     */
    public List<String> getLines() {
        return lines;
    }

    /**
     * Returns whether this stream collected any lines.
     * @return true if the output stream collected lines from the output.
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Returns whether this stream did not collect any lines.
     * @return true if the output stream did not collect any lines from the output.
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}