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
import io.jenkins.functions.Logger;
import io.jenkins.functions.Step;
import org.kohsuke.github.GHPullRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Step
public class ReleaseProject extends FunctionSupport implements Function<ReleaseProject.Arguments, Boolean> {
    public ReleaseProject() {
    }

    public ReleaseProject(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public Boolean apply(Arguments config) {
        GHPullRequest pullRequest = new PromoteArtifacts(this).apply(config.createPromoteArtifactsArguments());

        PromoteImages.Arguments promoteImagesArgs = config.createPromoteImagesArguments(getLogger());
        if (promoteImagesArgs != null) {
            new PromoteImages(this).apply(promoteImagesArgs);
        }

        TagImages.Arguments tagImagesArguments = config.createTagImagesArguments();
        if (tagImagesArguments != null) {
            new TagImages(this).apply(tagImagesArguments);
        }

        if (pullRequest != null) {
            WaitUntilPullRequestMerged.Arguments waitUntilPullRequestMergedArguments = config.createWaitUntilPullRequestMergedArguments(pullRequest);
            new WaitUntilPullRequestMerged(this).apply(waitUntilPullRequestMergedArguments);
        }

        WaitUntilArtifactSyncedWithCentral.Arguments waitUntilArtifactSyncedWithCentralArguments = config.createWaitUntilArtifactSyncedWithCentralArguments(getLogger());
        if (waitUntilArtifactSyncedWithCentralArguments != null) {
            new WaitUntilArtifactSyncedWithCentral(this).apply(waitUntilArtifactSyncedWithCentralArguments);
        }
        return true;
    }

    public static class Arguments {
        @Argument
        private StagedProjectInfo stagedProject;
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

        public Arguments() {
        }

        public Arguments(StagedProjectInfo stagedProject) {
            this.stagedProject = stagedProject;
        }

        @Override
        public String toString() {
            return "Arguments{" +
                    "stagedProject=" + stagedProject +
                    ", containerName='" + containerName + '\'' +
                    ", dockerOrganisation='" + dockerOrganisation + '\'' +
                    ", promoteToDockerRegistry='" + promoteToDockerRegistry + '\'' +
                    ", promoteDockerImages=" + promoteDockerImages +
                    ", extraImagesToTag=" + extraImagesToTag +
                    ", repositoryToWaitFor='" + repositoryToWaitFor + '\'' +
                    ", groupId='" + groupId + '\'' +
                    ", artifactExtensionToWaitFor='" + artifactExtensionToWaitFor + '\'' +
                    ", artifactIdToWaitFor='" + artifactIdToWaitFor + '\'' +
                    '}';
        }

        /**
         * Returns the arguments for invoking {@link PromoteArtifacts}
         */
        public PromoteArtifacts.Arguments createPromoteArtifactsArguments() {
            StagedProjectInfo stagedProject = getStagedProject();
            return new PromoteArtifacts.Arguments(stagedProject.getProject(), stagedProject.getReleaseVersion(), stagedProject.getRepoIds());
        }

        /**
         * Return the arguments for invoking {@link PromoteImages} or null if there is not sufficient configuration
         * to promote images
         */
        public PromoteImages.Arguments createPromoteImagesArguments(Logger logger) {
            StagedProjectInfo stagedProject = getStagedProject();
            String org = getDockerOrganisation();
            String toRegistry = getPromoteToDockerRegistry();
            List<String> images = getPromoteDockerImages();
            if (images != null && !images.isEmpty()) {
                if (Strings.isNullOrBlank(org)) {
                    logger.warn("Cannot promote images " + images + " as missing the dockerOrganisation argument: " + this);
                    return null;
                }
                if (Strings.isNullOrBlank(toRegistry)) {
                    logger.warn("Cannot promote images " + images + " as missing the promoteToDockerRegistry argument: " + this);
                    return null;
                }
                return new PromoteImages.Arguments(stagedProject.getReleaseVersion(), org, toRegistry, images);
            }
            return null;
        }

        /**
         * Returns the arguments for invoking {@link TagImages} or null if there are no images to tag
         */
        public TagImages.Arguments createTagImagesArguments() {
            StagedProjectInfo stagedProject = getStagedProject();
            if (extraImagesToTag != null && !extraImagesToTag.isEmpty()) {
                return new TagImages.Arguments(stagedProject.getReleaseVersion(), extraImagesToTag);
            } else {
                return null;
            }
        }

        /**
         * Returns the arguments for invoking {@link WaitUntilPullRequestMerged}
         *
         * @param pullRequestId
         */
        public WaitUntilPullRequestMerged.Arguments createWaitUntilPullRequestMergedArguments(GHPullRequest pullRequestId) {
            return new WaitUntilPullRequestMerged.Arguments(pullRequestId.getId(), getStagedProject().getProject());
        }

        /**
         * Returns the arguments for invoking {@link WaitUntilArtifactSyncedWithCentral}
         */
        public WaitUntilArtifactSyncedWithCentral.Arguments createWaitUntilArtifactSyncedWithCentralArguments(Logger logger) {
            if (Strings.isNullOrBlank(groupId) || Strings.isNullOrBlank(artifactIdToWaitFor)) {
                logger.warn("Cannot wait for artifacts to be synced to central repository as require groupId and artifactIdToWaitFor properties. Was given " + this);
                return null;
            }

            WaitUntilArtifactSyncedWithCentral.Arguments arguments = new WaitUntilArtifactSyncedWithCentral.Arguments(groupId, artifactIdToWaitFor, getStagedProject().getReleaseVersion());
            if (Strings.notEmpty(artifactExtensionToWaitFor)) {
                arguments.setExt(artifactExtensionToWaitFor);
            }
            if (Strings.notEmpty(repositoryToWaitFor)) {
                arguments.setRepo(repositoryToWaitFor);
            }
            return arguments;
        }

        public StagedProjectInfo getStagedProject() {
            return stagedProject;
        }

        public void setStagedProject(StagedProjectInfo stagedProject) {
            this.stagedProject = stagedProject;
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
