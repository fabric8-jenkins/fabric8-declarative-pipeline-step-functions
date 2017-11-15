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
import io.jenkins.functions.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Step
public class StageProject extends FunctionSupport implements Function<StageProject.Arguments, StagedProjectInfo> {
    public StageProject() {
    }

    public StageProject(FunctionSupport parentStep) {
        super(parentStep);
    }

    public StagedProjectInfo apply(String project) {
        return apply(new Arguments(project));
    }

    @Override
    @Step
    public StagedProjectInfo apply(Arguments config) {
        final Fabric8Commands flow = new Fabric8Commands(this);

        final AtomicReference<List<String>> repoIdsRef = new AtomicReference<>();
        final AtomicReference<String> releaseVersionRef = new AtomicReference<>();

        final List<String> extraImagesToStage = config.getExtraImagesToStage();
        final String containerName = config.getContainerName();
        final String project = config.getProject();

        container(containerName, () -> {
            sh("chmod 600 /root/.ssh-git/ssh-key");
            sh("chmod 600 /root/.ssh-git/ssh-key.pub");
            sh("chmod 700 /root/.ssh-git");
            sh("chmod 600 /home/jenkins/.gnupg/pubring.gpg");
            sh("chmod 600 /home/jenkins/.gnupg/secring.gpg");
            sh("chmod 600 /home/jenkins/.gnupg/trustdb.gpg");
            sh("chmod 700 /home/jenkins/.gnupg");

            sh("git remote set-url origin git@github.com:" + project + ".git");

            String currentVersion = flow.getProjectVersion();

            Boolean useGitTagForNextVersion = config.getUseGitTagForNextVersion();
            flow.setupWorkspaceForRelease(project, useGitTagForNextVersion, config.getExtraSetVersionArgs(), currentVersion);

            repoIdsRef.set(flow.stageSonartypeRepo());
            releaseVersionRef.set(flow.getProjectVersion());

            // lets avoide the stash / unstash for now as we're not using helm ATM
            //stash excludes: '*/src/', includes: '**', name: "staged-${config.project}-${releaseVersion}".hashCode().toString()

            if (useGitTagForNextVersion == null || !useGitTagForNextVersion.booleanValue()) {
                return flow.updateGithub();
            }
            return null;
        });

        String releaseVersion = releaseVersionRef.get();
        if (extraImagesToStage != null) {
            new StageExtraImages(this).apply(releaseVersion, extraImagesToStage);
        }
        return new StagedProjectInfo(project, releaseVersion, repoIdsRef.get());
    }

    public static class Arguments {
        @Argument
        private String project;
        @Argument
        private Boolean useGitTagForNextVersion;
        @Argument
        private String extraSetVersionArgs;
        @Argument
        private List<String> extraImagesToStage = new ArrayList<>();
        @Argument
        private String containerName = "maven";

        public Arguments() {
        }

        public Arguments(String project) {
            this.project = project;
        }

        public Boolean getUseGitTagForNextVersion() {
            return useGitTagForNextVersion;
        }

        public void setUseGitTagForNextVersion(Boolean useGitTagForNextVersion) {
            this.useGitTagForNextVersion = useGitTagForNextVersion;
        }

        public String getExtraSetVersionArgs() {
            return extraSetVersionArgs;
        }

        public void setExtraSetVersionArgs(String extraSetVersionArgs) {
            this.extraSetVersionArgs = extraSetVersionArgs;
        }

        public List<String> getExtraImagesToStage() {
            return extraImagesToStage;
        }

        public void setExtraImagesToStage(List<String> extraImagesToStage) {
            this.extraImagesToStage = extraImagesToStage;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }
    }
}
