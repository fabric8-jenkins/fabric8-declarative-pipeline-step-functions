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
package io.fabric8.pipeline.steps.helpers;

import io.fabric8.utils.Files;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import io.jenkins.functions.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 */
public class ProcessHelper {

    public static String runCommandCaptureOutput(File dir, Logger logger, Map<String, String> environmentVariables, String... commands) throws IOException {
        File outputFile;
        File errorFile;
        try {
            outputFile = File.createTempFile("steps-", ".log");
            errorFile = File.createTempFile("steps-", ".err");
        } catch (IOException e) {
            throw new IOException("Failed to create temporary files " + e, e);
        }

        int result = runCommand(dir, logger, environmentVariables, outputFile, errorFile, commands);
        String output = loadFile(logger, outputFile);
        String err = loadFile(logger, errorFile);
        logOutput(logger, err, true);
        if (result != 0) {
            logger.warn("Failed to run commands " + String.join(" ", commands) + " result: " + result);
            logOutput(logger, output, false);
            throw new IOException("Failed to run commands " + String.join(" ", commands) + " result: " + result);
        }
        return output;
    }

    public static int runCommand(File dir, Logger logger, Map<String, String> environmentVariables, File outputFile, File errorFile, String... commands) {
        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.directory(dir);
        applyEnvironmentVariables(builder, environmentVariables);
        builder.redirectOutput(outputFile);
        builder.redirectError(errorFile);
        return doRunCommand(logger, builder, commands);
    }

    public static int runCommand(File dir, Logger logger, Map<String, String> environmentVariables, String[] commands) {
        File outputFile = new File(dir, "target/steps.log");
        File errorFile = new File(dir, "target/steps.err");
        try (FileDeleter ignored = new FileDeleter(outputFile, errorFile)) {
            outputFile.getParentFile().mkdirs();
            int answer = runCommand(dir, logger, environmentVariables, outputFile, errorFile, commands);
            if (answer != 0) {
                logger.warn("Failed to run " + String.join(" ", commands));
            }
            logOutput(logger, outputFile, false);
            logOutput(logger, errorFile, true);
            return answer;
        } catch (IOException e) {
            logger.warn("Caught: " + e, e);
            return -1;
        }
    }

    public static boolean runCommandAndLogOutput(Logger log, File dir, String... commands) {
        return runCommandAndLogOutput(log, dir, Collections.EMPTY_MAP, commands);
    }

    public static boolean runCommandAndLogOutput(Logger log, File dir, boolean useError, String... commands) {
        return runCommandAndLogOutput(log, dir, Collections.EMPTY_MAP, useError, commands);
    }

    public static boolean runCommandAndLogOutput(Logger log, File dir, Map<String, String> environmentVariables, String... commands) {
        return runCommandAndLogOutput(log, dir, environmentVariables, true, commands);
    }

    public static boolean runCommandAndLogOutput(Logger logger, File dir, Map<String, String> environmentVariables, boolean useError, String... commands) {
        File outputFile = new File(dir, "target/updatebot.log");
        File errorFile = new File(dir, "target/updatebot.err");
        try (FileDeleter ignored = new FileDeleter(outputFile, errorFile)) {
            outputFile.getParentFile().mkdirs();
            boolean answer = true;
            if (runCommand(dir, logger, environmentVariables, outputFile, errorFile, commands) != 0) {
                logger.error("Failed to run " + String.join(" ", commands));
                answer = false;
            }
            logOutput(logger, outputFile, false);
            logOutput(logger, errorFile, useError);
            return answer;
        } catch (IOException e) {
            logger.warn("Caught: " + e, e);
            return false;
        }
    }

    public static void logOutput(Logger log, File file, boolean error) {
        logOutput(log, loadFile(log, file), error);
    }

    protected static void logOutput(Logger log, String output, boolean error) {
        if (Strings.notEmpty(output)) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (error) {
                    log.error(line);
                } else {
                    log.info(line);
                }
            }
        }
    }

    protected static String loadFile(Logger logger, File file) {
        String output = null;
        if (Files.isFile(file)) {
            try {
                output = IOHelpers.readFully(file);
            } catch (IOException e) {
                logger.error("Failed to load " + file + ". " + e, e);
            }
        }
        return output;
    }


    protected static void applyEnvironmentVariables(ProcessBuilder builder, Map<String, String> environmentVariables) {
        if (environmentVariables != null) {
            for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                builder.environment().put(entry.getKey(), entry.getValue());
            }
        }
    }

    protected static int doRunCommand(Logger logger, ProcessBuilder builder, String[] commands) {
        String line = String.join(" ", commands);
        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("Failed to run command " + line + " in " + builder.directory() + " : exit " + exitCode);
            }
            return exitCode;
        } catch (IOException e) {
            logger.warn("Failed to run command " + line + " in " + builder.directory() + " : error " + e);
        } catch (InterruptedException e) {
            // ignore
        }
        return 1;
    }

}
