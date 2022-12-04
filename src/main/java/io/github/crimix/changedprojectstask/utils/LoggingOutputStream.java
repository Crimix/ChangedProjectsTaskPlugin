package io.github.crimix.changedprojectstask.utils;

import org.apache.commons.exec.LogOutputStream;

import java.util.function.Consumer;

public class LoggingOutputStream extends LogOutputStream {

    private final Consumer<String> logFunc;

    public LoggingOutputStream(Consumer<String> logFunc) {
        this.logFunc = logFunc;
    }

    @Override
    protected void processLine(String line, int level) {
        logFunc.accept(line);
    }

}