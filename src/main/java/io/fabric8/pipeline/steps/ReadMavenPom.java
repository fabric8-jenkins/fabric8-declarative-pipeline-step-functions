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
package io.fabric8.pipeline.steps;

import io.fabric8.FunctionSupport;
import io.fabric8.utils.Strings;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper function for reading the maven <code>pom.xml</code> file
 */
@Step
public class ReadMavenPom extends FunctionSupport {
    public ReadMavenPom() {
    }

    public ReadMavenPom(FunctionSupport parentStep) {
        super(parentStep);
    }

    public Model apply() throws IOException, XmlPullParserException {
        return apply(new Arguments());
    }

    public Model apply(String fileName) throws IOException, XmlPullParserException {
        return apply(new Arguments(fileName));
    }

    @Step
    public Model apply(Arguments arguments) throws IOException, XmlPullParserException {
        File file = arguments.getFile();
        if (file == null) {
            String fileName = arguments.getFileName();
            if (Strings.notEmpty(fileName)) {
                file = createFile(fileName);
            }
        }
        if (file == null) {
            file = createFile("pom.xml");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("" + file + " does not exist.");
        }
        if (file.isDirectory()) {
            throw new FileNotFoundException("" + file + " is a directory.");
        }
        try (InputStream is = new FileInputStream(file)) {
            return new MavenXpp3Reader().read(is);
        }
    }

    public static class Arguments {
        private File file;
        @Argument
        private String fileName;

        public Arguments() {
        }

        public Arguments(String fileName) {
            this.fileName = fileName;
        }

        public File getFile() {
            return file;
        }

        public void setFile(File file) {
            this.file = file;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }
    }
}
