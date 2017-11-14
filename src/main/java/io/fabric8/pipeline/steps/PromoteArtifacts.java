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

import io.fabric8.Fabric8Commands;
import io.fabric8.FunctionSupport;
import io.jenkins.functions.Argument;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class PromoteArtifacts extends FunctionSupport implements Function<PromoteArtifacts.Arguments, String> {

    public PromoteArtifacts() {
    }

    public PromoteArtifacts(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    public String apply(Arguments config) {
        final String name = config.getName();
        final String version = config.getVersion();
        final List<String> repoIds = config.getRepoIds();
        final String containerName = config.containerName;

        container(containerName, new Callable<String>() {
            @Override
            public String call() throws Exception {
                sh("chmod 600 /root/.ssh-git/ssh-key");
                sh("chmod 600 /root/.ssh-git/ssh-key.pub");
                sh("chmod 700 /root/.ssh-git");

                Fabric8Commands flow = new Fabric8Commands(PromoteArtifacts.this);

                echo("About to release " + name + " repo ids " + repoIds);
                for (String repoId : repoIds) {
                    flow.releaseSonartypeRepo(repoId);
                }

                if (config.isHelmPush()) {
                    flow.helm();
                }

                if (config.isUpdateNextDevelopmentVersion()) {
                    String args = config.getUpdateNextDevelopmentVersionArguments();
                    if (args == null) {
                        args = "";
                    }
                    flow.updateNextDevelopmentVersion(version, args);
                    flow.createPullRequest("[CD] Release " + version, config.getProject(), "release-v" + version);
                }
                return null;
            }
        });
        return null;
    }

    public static class Arguments {
        @Argument
        private String name;
        @Argument
        private String project;
        @Argument
        private String version;
        @Argument
        private List<String> repoIds = new ArrayList<>();
        @Argument
        private String containerName = "maven";
        @Argument
        private boolean helmPush;
        @Argument
        private boolean updateNextDevelopmentVersion;
        @Argument
        private String updateNextDevelopmentVersionArguments;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public List<String> getRepoIds() {
            return repoIds;
        }

        public void setRepoIds(List<String> repoIds) {
            this.repoIds = repoIds;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public boolean isHelmPush() {
            return helmPush;
        }

        public void setHelmPush(boolean helmPush) {
            this.helmPush = helmPush;
        }

        public boolean isUpdateNextDevelopmentVersion() {
            return updateNextDevelopmentVersion;
        }

        public void setUpdateNextDevelopmentVersion(boolean updateNextDevelopmentVersion) {
            this.updateNextDevelopmentVersion = updateNextDevelopmentVersion;
        }

        public String getUpdateNextDevelopmentVersionArguments() {
            return updateNextDevelopmentVersionArguments;
        }

        public void setUpdateNextDevelopmentVersionArguments(String updateNextDevelopmentVersionArguments) {
            this.updateNextDevelopmentVersionArguments = updateNextDevelopmentVersionArguments;
        }
    }
}
