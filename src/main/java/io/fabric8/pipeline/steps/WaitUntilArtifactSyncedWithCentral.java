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

import java.util.function.Function;

/**
 * Waits for a maven artifact to be in the maven central repository
 */
@Step(displayName = "Waits for an artifact to be synchronized to a central registry")
public class WaitUntilArtifactSyncedWithCentral extends FunctionSupport implements Function<WaitUntilArtifactSyncedWithCentral.Arguments, String> {
    public WaitUntilArtifactSyncedWithCentral() {
    }

    public WaitUntilArtifactSyncedWithCentral(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public String apply(Arguments config) {
        final Fabric8Commands flow = new Fabric8Commands(this);

        final String groupId = config.groupId;
        final String artifactId = config.artifactId;
        final String version = config.version;
        final String ext = config.ext;

        if (Strings.isNullOrEmpty(groupId) || Strings.isNullOrEmpty(artifactId) || Strings.isNullOrEmpty(version)) {
            error("Must specify full maven coordinates but was given: " + config);
            return null;
        }

        waitUntil(() -> retry(3, () -> flow.isArtifactAvailableInRepo(config.repo, groupId, artifactId, version, ext)));

        String message = "" + groupId + "/" + artifactId + " " + version + " released and available in maven central";
        echo(message);
        hubotSend(message);
        return null;
    }

    public static class Arguments {
        @Argument
        private String repo = ServiceConstants.MAVEN_CENTRAL;
        @Argument
        private String groupId = "";
        @Argument
        private String artifactId = "";
        @Argument
        private String version = "";
        @Argument
        private String ext = "jar";

        public Arguments() {
        }

        public Arguments(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override
        public String toString() {
            return "Arguments{" +
                    "repo='" + repo + '\'' +
                    ", groupId='" + groupId + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", version='" + version + '\'' +
                    ", ext='" + ext + '\'' +
                    '}';
        }

        public String getRepo() {
            return repo;
        }

        public void setRepo(String repo) {
            this.repo = repo;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getExt() {
            return ext;
        }

        public void setExt(String ext) {
            this.ext = ext;
        }
    }

}
