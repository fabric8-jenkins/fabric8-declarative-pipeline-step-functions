/**
 * Copyright (C) Original Authors 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8;

import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.fabric8.pipeline.steps.helpers.Loggers;
import io.fabric8.pipeline.steps.helpers.ProcessHelper;
import io.fabric8.utils.IOHelpers;
import io.jenkins.functions.Logger;
import io.jenkins.functions.support.DefaultLogger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;


/**
 * A useful base class for implementing functions reusing common semantics from pipeline libraries
 */
public abstract class FunctionSupport {
    private Logger logger = DefaultLogger.getInstance();
    private File currentDir = new File(".");

    public FunctionSupport() {
    }

    public FunctionSupport(FunctionSupport parentStep) {
        this.logger = parentStep.getLogger();
        if (this.logger == null) {
            logger = DefaultLogger.getInstance();
        }
        this.currentDir = parentStep.getCurrentDir();
    }


    public void callStep(String stepName, Map<String, Object> arguments) {
        // TODO...
    }

    public void echo(String message) {
        Loggers.echo(getLogger(), message);
    }

    public void error(String message) {
        Loggers.error(getLogger(), message);
    }

    public void error(String message, Throwable t) {
        Loggers.error(getLogger(), message, t);
    }

    protected File createFile(String name) {
        return new File(currentDir, name);
    }

    public String sh(String command) {
        try {
            return execAndGetOutput("bash", "-c", command);
        } catch (IOException e) {
            throw new FailedBuildException("Failed to run command: " + command, e);
        }
    }

    public String execAndGetOutput(String... commands) throws IOException {
        return ProcessHelper.runCommandCaptureOutput(currentDir, commands);
    }

    public String readFile(String fileName) throws IOException {
        return IOHelpers.readFully(createFile(fileName));
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(File currentDir) {
        this.currentDir = currentDir;

        // TODO should we also set a system property for 'pwd' etc?
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * Invokes a pipeline step
     */
    protected <T> Object step(String stepName, Map<String, T> arguments) {
        // TODO
        return null;
    }

    protected List<File> findFiles(String glob) {
        List<File> answer = new ArrayList<>();
        // TODO
        return answer;
    }

    /**
     * Retries the given block until
     *
     * @param count
     * @param block
     * @param <T>
     * @return
     */
    protected <T> T retry(int count, Callable<T> block) {
        Exception lastException = null;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                getLogger().out().println("Retrying");
            }
            try {
                return block.call();
            } catch (Exception e) {
                lastException = e;
                getLogger().err().println("Failed " + e + ". ");
                e.printStackTrace(getLogger().err());
            }
        }
        if (lastException != null) {
            throw new FailedBuildException(lastException);
        }
        return null;
    }

    /**
     * Invokes the given block in the given directory then restores to the current directory at the end of the block
     */
    protected <T> T dir(File dir, Callable<T> callable) throws Exception {
        File currentDir = getCurrentDir();
        setCurrentDir(dir);
        try {
            return callable.call();
        } finally {
            setCurrentDir(currentDir);
        }
    }

}
