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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
        ProcessBuilder builder = new ProcessBuilder(commands);
        builder.directory(dir);
        applyEnvironmentVariables(builder, environmentVariables);
        return doRunCommandAndLogOutput(logger, builder, commands);

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
        String line = getCommandLine(commands);
        try {
            logger.info("$> " + line);
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

    protected static int doRunCommandAndLogOutput(Logger logger, ProcessBuilder builder, String[] commands) {
        String line = getCommandLine(commands);
        try {
            logger.info("$> " + line);
            Process process = builder.start();
            processOutput(process.getInputStream(), logger, false, "output of command: " + line);
            processOutput(process.getErrorStream(), logger, true, "errors of command: " + line);

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

    protected static String getCommandLine(String[] commands) {
        return Strings.stripPrefix(String.join(" ", commands), "bash -c ");
    }

    protected static void processOutput(InputStream inputStream, Logger logger, boolean error, String description) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) break;
                if (error) {
                    logger.error(line);
                } else {
                    logger.info(line);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process " + description + ": " + e, e);
            throw e;
        }
    }


}
