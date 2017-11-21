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

import com.google.common.base.Strings;
import io.fabric8.Fabric8Commands;
import io.fabric8.FunctionSupport;
import io.fabric8.pipeline.steps.model.ServiceConstants;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;

import javax.validation.constraints.NotEmpty;
import java.util.function.Function;

/**
 * Waits for a jenkins plugin to be available in the Jenkins archive
 */
@Step(displayName = "Waits for a jenkins plugin to be synchronized with the jenkins plugin archive")
public class WaitUntilJenkinsPluginSynced extends FunctionSupport implements Function<WaitUntilJenkinsPluginSynced.Arguments, Boolean> {
    public WaitUntilJenkinsPluginSynced() {
    }

    public WaitUntilJenkinsPluginSynced(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public Boolean apply(Arguments config) {
        final Fabric8Commands flow = new Fabric8Commands(this);

        final String version = config.version;
        final String repo = config.repo;
        final String name = config.name;

        final String path = "plugins/" + name;
        final String artifact = name + ".hpi";

        if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(repo) || Strings.isNullOrEmpty(version)) {
            error("Missing Jenkins plugin arguments - was given: " + config);
            return null;
        }


        if (waitUntil(() -> retry(3, () -> flow.isFileAvailableInRepo(repo, path, version, artifact)))) {
            String message = "Jenkins plugin " + artifact + " " + version + " released and available in the jenkins plugin archive";
            echo(message);
            hubotSend(message);
            return true;
        } else {
            echo("Timed out waiting for Jenkins plugin " + artifact + " " + version + " to be available in the jenkins plugin archive");
        }
        return false;
    }


    public static class Arguments {
        @Argument
        private String repo = ServiceConstants.JENKINS_ARCHIVE_REPO;
        @Argument
        @NotEmpty
        private String name = "";
        @Argument
        @NotEmpty
        private String version = "";

        @Override
        public String toString() {
            return "Arguments{" +
                    "repo='" + repo + '\'' +
                    ", name='" + name + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }

        public String getRepo() {
            return repo;
        }

        public void setRepo(String repo) {
            this.repo = repo;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

}
