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

import io.fabric8.pipeline.steps.helpers.ProcessHelper;
import io.fabric8.utils.IOHelpers;

import java.io.File;
import java.io.IOException;
import java.util.Map;


/**
 */
public abstract class FunctionSupport {
    private File currentDir = new File(".");

    protected File createFile(String name) {
        return new File(currentDir, name);
    }

    public  String execBashAndGetOutput(String command) throws IOException {
        return execAndGetOutput("bash", "-c", command);
    }

    public  String execAndGetOutput(String... commands) throws IOException {
        return ProcessHelper.runCommandCaptureOutput(currentDir, commands);
    }

    public String readFile(String fileName) throws IOException {
        return IOHelpers.readFully(createFile(fileName));
    }


    public static void callStep(String stepName, Map<String, Object> arguments) {
        // TODO...
    }


    public static void echo(String message) {
        System.out.println(message);
    }

    public static void error(String message) {
        echo("ERROR: " + message);
    }

    public static void error(String message, Throwable t) {
        echo("ERROR: " + message + " " + t);
        t.printStackTrace();
    }


    public File getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(File currentDir) {
        this.currentDir = currentDir;
    }
}
