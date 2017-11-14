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
import io.fabric8.Utils;
import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.fabric8.utils.Strings;
import io.jenkins.functions.Step;
import org.apache.maven.model.Model;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.fabric8.pipeline.steps.helpers.Systems.getEnvVar;
import static io.fabric8.utils.Strings.notEmpty;

/**
 * Declarative step function to perform a maven release
 */
@Step
public class MavenRelease extends FunctionSupport implements Function<MavenRelease.Arguments, String> {

    public MavenRelease() {
    }

    public MavenRelease(FunctionSupport parentStep) {
        super(parentStep);
    }

    public String apply(Arguments args) {
        Fabric8Commands flow = new Fabric8Commands(this);
        Utils utils = new Utils(this);

        boolean skipTests = args.isSkipTests();
        String version = args.getVersion();
        if (Strings.isNullOrBlank(version)) {
            try {
                version = flow.getNewVersionFromTag();
            } catch (IOException e) {
                throw new FailedBuildException("Could not find release version due to " + e, e);
            }
        }

        sh("git checkout -b " + getEnvVar("JOB_NAME", "cd-release") + "-" + version);
        sh("mvn org.codehaus.mojo:versions-maven-plugin:2.2:set -U -DnewVersion=" + version);
        sh("mvn clean -B -e -U deploy -Dmaven.test.skip=" + skipTests + " -P openshift");


        new JUnitResults(this).apply(args.getJUnitArguments());

        String buildName = "";
        try {
            buildName = utils.getValidOpenShiftBuildName();
        } catch (Exception err) {
            error("Failed to find buildName", err);
        }

        if (notEmpty(buildName)) {
            String buildUrl = System.getenv("BUILD_URL");
            if (notEmpty(buildUrl)) {
                utils.addAnnotationToBuild("fabric8.io/jenkins.testReportUrl", buildUrl + "testReport");
            }

            String changeUrl = System.getenv("CHANGE_URL");
            if (notEmpty(changeUrl)) {
                utils.addAnnotationToBuild("fabric8.io/jenkins.changeUrl", (String) changeUrl);
            }

            new BayesianScanner(this).apply(args.getBayesianScannerArguments());
        }


        new SonarQubeScanner(this).apply(args.getSonarQubeArguments());


        final boolean s2iMode = utils.supportsOpenShiftS2I();
        echo("s2i mode: " + s2iMode);

        if (!s2iMode) {
            final String registry = utils.getDockerRegistry();
            if (flow.isSingleNode()) {
                echo("Running on a single node, skipping docker push as not needed");
                Model model = null;
                try {
                    model = new ReadMavenPom(this).apply(args.getReadMavenPomArguments());
                } catch (Exception e) {
                    error("Failed to read pom.xml", e);
                }
                boolean tagged = false;
                if (model != null) {
                    String groupId = model.getGroupId();
                    String artifactId = model.getArtifactId();
                    if (notEmpty(groupId) && notEmpty(artifactId)) {
                        String[] groupIds = groupId.split("\\.");
                        String user = groupIds[groupIds.length - 1].trim();
                        sh("docker tag " + user + "/" + artifactId + ":" + version + " " + registry + "/" + user + "/" + artifactId + ":" + version);
                        tagged = true;
                    }
                }
                if (!tagged) {
                    error("Could not tag the docker image as could not find the groupId and artifactId from the pom.xml!");
                }
            } else {
                retry(5, new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        sh("mvn fabric8:push -Ddocker.push.registry=" + registry);
                        return null;
                    }
                });

            }


        }
        new ContentRepository(this).apply(args.getContentRepositoryArguments());
        return null;
    }

    public static class Arguments {
        private boolean skipTests;
        private String version;
        private JUnitResults.Arguments JUnitArguments = new JUnitResults.Arguments();
        private BayesianScanner.Arguments bayesianScannerArguments = new BayesianScanner.Arguments();
        private SonarQubeScanner.Arguments sonarQubeArguments = new SonarQubeScanner.Arguments();
        private ContentRepository.Arguments contentRepositoryArguments = new ContentRepository.Arguments();
        private ReadMavenPom.Arguments readMavenPomArguments = new ReadMavenPom.Arguments();

        public boolean isSkipTests() {
            return skipTests;
        }

        public void setSkipTests(boolean skipTests) {
            this.skipTests = skipTests;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public JUnitResults.Arguments getJUnitArguments() {
            return JUnitArguments;
        }

        public void setJUnitArguments(JUnitResults.Arguments JUnitArguments) {
            this.JUnitArguments = JUnitArguments;
        }

        public BayesianScanner.Arguments getBayesianScannerArguments() {
            return bayesianScannerArguments;
        }

        public void setBayesianScannerArguments(BayesianScanner.Arguments bayesianScannerArguments) {
            this.bayesianScannerArguments = bayesianScannerArguments;
        }

        public SonarQubeScanner.Arguments getSonarQubeArguments() {
            return sonarQubeArguments;
        }

        public void setSonarQubeArguments(SonarQubeScanner.Arguments sonarQubeArguments) {
            this.sonarQubeArguments = sonarQubeArguments;
        }

        public ContentRepository.Arguments getContentRepositoryArguments() {
            return contentRepositoryArguments;
        }

        public void setContentRepositoryArguments(ContentRepository.Arguments contentRepositoryArguments) {
            this.contentRepositoryArguments = contentRepositoryArguments;
        }

        public ReadMavenPom.Arguments getReadMavenPomArguments() {
            return readMavenPomArguments;
        }

        public void setReadMavenPomArguments(ReadMavenPom.Arguments readMavenPomArguments) {
            this.readMavenPomArguments = readMavenPomArguments;
        }
    }

}
