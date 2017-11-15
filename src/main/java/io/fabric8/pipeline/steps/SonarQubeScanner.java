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
import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static io.fabric8.pipeline.steps.ServiceConstants.MAVEN_CENTRAL;

@Step
public class SonarQubeScanner extends FunctionSupport implements Function<SonarQubeScanner.Arguments, String> {

    public SonarQubeScanner() {
    }

    public SonarQubeScanner(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public String apply(Arguments config) {
        final String serviceName = config.getServiceName();
        final int port = config.getServicePort();
        final String scannerVersion = config.getScannerVersion();

        if (config.isRunSonarScanner()) {
            Fabric8Commands flow = new Fabric8Commands(this);
            echo("Checking " + serviceName + " exists");
            if (flow.hasService(serviceName)) {
                try {
                    final File srcDirectory = new Pwd(this).apply();
                    File tmpDir = new Pwd(this).apply(true);

                    //work in tmpDir - as sonar scanner will download files from the server
                    dir(tmpDir, new Callable<String>() {
                        public String call() {
                            String localScanner = "scanner-cli.jar";

                            String scannerURL = MAVEN_CENTRAL + "org/sonarsource/scanner/cli/sonar-scanner-cli/" + scannerVersion + "/sonar-scanner-cli-" + scannerVersion + ".jar";

                            echo("downloading scanner-cli");

                            sh("curl -o " + localScanner + "  " + scannerURL);

                            echo("executing sonar scanner");

                            sh("java -jar " + localScanner + "  -Dsonar.host.url=http://" + serviceName + ":" + port + "  -Dsonar.projectKey=" + System.getenv("JOB_NAME") + " -Dsonar.sources=" + srcDirectory);
                            return null;
                        }
                    });

                } catch (Exception err) {
                    error("Failed to execute scanner", err);
                    throw new FailedBuildException(err);
                }


            } else {
                echo("Code validation service: " + serviceName + " not available");
            }
        }
        return null;
    }

    public static class Arguments {
        @Argument
        private String serviceName = "sonarqube";
        @Argument
        private int servicePort = 9000;
        @Argument
        private String scannerVersion = "2.8";
        @Argument
        private boolean runSonarScanner = true;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public int getServicePort() {
            return servicePort;
        }

        public void setServicePort(int servicePort) {
            this.servicePort = servicePort;
        }

        public boolean isRunSonarScanner() {
            return runSonarScanner;
        }

        public void setRunSonarScanner(boolean runSonarScanner) {
            this.runSonarScanner = runSonarScanner;
        }

        public String getScannerVersion() {
            return scannerVersion;
        }

        public void setScannerVersion(String scannerVersion) {
            this.scannerVersion = scannerVersion;
        }
    }

}
