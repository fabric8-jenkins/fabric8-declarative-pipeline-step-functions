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
import io.fabric8.Utils;
import io.fabric8.pipeline.steps.git.GitHelper;
import io.fabric8.pipeline.steps.git.GitRepositoryInfo;
import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.fabric8.utils.Strings;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Performs a full CI / CD pipeline for maven projects
 */
public class MavenPipeline  extends FunctionSupport implements Function<MavenPipeline.Arguments, Boolean> {
    @Override
    public Boolean apply(Arguments arguments) {
        checkoutScm();
        Utils utils = new Utils(this);

        if (utils.isCI()) {
            return ciPipeline(arguments);
        } else if (utils.isCD()) {
            return cdPipeline(arguments);
        } else {
            getLogger().error("Unknown configuration - neither a CI or CD pipeline!");
        }
        return false;
    }

    /**
     * Implements the CI pipeline
     */
    protected Boolean ciPipeline(Arguments arguments) {
        // TODO
        echo("No CI pipeline supported yet ;)");
        return false;
    }

    /**
     * Implements the CD pipeline
     */
    protected Boolean cdPipeline(Arguments arguments) {
        String gitCloneUrl = arguments.getGitCloneUrl();
        if (Strings.isNullOrBlank(gitCloneUrl)) {
            error("No gitCloneUrl configured for this pipeline!");
            throw new FailedBuildException("No gitCloneUrl configured for this pipeline!");
        }
        GitRepositoryInfo repositoryInfo = GitHelper.parseGitRepositoryInfo(gitCloneUrl);
        sh(  "git remote set-url " + gitCloneUrl);
        StageProject.Arguments stageProjectArguments = arguments.createStageProjectArguments(getLogger(), repositoryInfo);
        StagedProjectInfo stagedProject = new StageProject(this).apply(stageProjectArguments);

        ReleaseProject.Arguments releaseProjectArguments = arguments.createReleaseProjectArguments(getLogger(), stagedProject);
        return new ReleaseProject(this).apply(releaseProjectArguments);
    }

    public static class Arguments {
        @Argument
        private String gitCloneUrl;
        @Argument
        private Boolean useGitTagForNextVersion;
        @Argument
        private String extraSetVersionArgs;
        @Argument
        private List<String> extraImagesToStage = new ArrayList<>();
        @Argument
        private String containerName = "maven";

        @Argument
        private String dockerOrganisation;
        @Argument
        private String promoteToDockerRegistry;
        @Argument
        private List<String> promoteDockerImages = new ArrayList<>();
        @Argument
        private List<String> extraImagesToTag = new ArrayList<>();
        @Argument
        private String repositoryToWaitFor = ServiceConstants.MAVEN_CENTRAL;
        @Argument
        private String groupId;
        @Argument
        private String artifactExtensionToWaitFor;
        @Argument
        private String artifactIdToWaitFor;


        public String getGitCloneUrl() {
            return gitCloneUrl;
        }

        public void setGitCloneUrl(String gitCloneUrl) {
            this.gitCloneUrl = gitCloneUrl;
        }

        public StageProject.Arguments createStageProjectArguments(Logger logger, GitRepositoryInfo repositoryInfo) {
            StageProject.Arguments answer = new StageProject.Arguments(repositoryInfo.getProject());
            answer.setUseGitTagForNextVersion(useGitTagForNextVersion);
            answer.setExtraSetVersionArgs(extraSetVersionArgs);
            answer.setExtraImagesToStage(extraImagesToStage);
            if (Strings.notEmpty(containerName)) {
                answer.setContainerName(containerName);
            }
            return answer;
        }

        public ReleaseProject.Arguments createReleaseProjectArguments(Logger logger, StagedProjectInfo stagedProject) {
            ReleaseProject.Arguments answer = new ReleaseProject.Arguments(stagedProject);
            if (Strings.notEmpty(containerName)) {
                answer.setContainerName(containerName);
            }
            answer.setArtifactExtensionToWaitFor(getArtifactExtensionToWaitFor());
            answer.setArtifactIdToWaitFor(getArtifactIdToWaitFor());
            answer.setDockerOrganisation(getDockerOrganisation());
            answer.setExtraImagesToTag(getExtraImagesToTag());
            answer.setGroupId(getGroupId());
            answer.setPromoteDockerImages(getPromoteDockerImages());
            answer.setPromoteToDockerRegistry(getPromoteToDockerRegistry());
            answer.setRepositoryToWaitFor(getRepositoryToWaitFor());
            return answer;
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

        public String getContainerName() {
            return containerName;
        }

        public void setContainerName(String containerName) {
            this.containerName = containerName;
        }

        public String getDockerOrganisation() {
            return dockerOrganisation;
        }

        public void setDockerOrganisation(String dockerOrganisation) {
            this.dockerOrganisation = dockerOrganisation;
        }

        public String getPromoteToDockerRegistry() {
            return promoteToDockerRegistry;
        }

        public void setPromoteToDockerRegistry(String promoteToDockerRegistry) {
            this.promoteToDockerRegistry = promoteToDockerRegistry;
        }

        public List<String> getPromoteDockerImages() {
            return promoteDockerImages;
        }

        public void setPromoteDockerImages(List<String> promoteDockerImages) {
            this.promoteDockerImages = promoteDockerImages;
        }

        public List<String> getExtraImagesToTag() {
            return extraImagesToTag;
        }

        public void setExtraImagesToTag(List<String> extraImagesToTag) {
            this.extraImagesToTag = extraImagesToTag;
        }

        public String getRepositoryToWaitFor() {
            return repositoryToWaitFor;
        }

        public void setRepositoryToWaitFor(String repositoryToWaitFor) {
            this.repositoryToWaitFor = repositoryToWaitFor;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactExtensionToWaitFor() {
            return artifactExtensionToWaitFor;
        }

        public void setArtifactExtensionToWaitFor(String artifactExtensionToWaitFor) {
            this.artifactExtensionToWaitFor = artifactExtensionToWaitFor;
        }

        public String getArtifactIdToWaitFor() {
            return artifactIdToWaitFor;
        }

        public void setArtifactIdToWaitFor(String artifactIdToWaitFor) {
            this.artifactIdToWaitFor = artifactIdToWaitFor;
        }
    }
}
