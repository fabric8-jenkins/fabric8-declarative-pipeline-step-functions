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

import io.fabric8.pipeline.steps.helpers.Systems;

/**
 */
public class ServiceConstants {
    public static final String FABRIC8_DOCKER_REGISTRY = "fabric8-docker-registry";
    public static final String FABRIC8_DOCKER_REGISTRY_PORT = "80";
    public static final String MAVEN_CENTRAL = "http://central.maven.org/maven2/";
    public static final String JENKINS_ARCHIVE_REPO = "http://archives.jenkins-ci.org/";

    public static String getDockerRegistryPort() {
        return Systems.getEnvVar("FABRIC8_DOCKER_REGISTRY_SERVICE_PORT", FABRIC8_DOCKER_REGISTRY_PORT);
    }

    public static String getDockerRegistryHost() {
        return Systems.getEnvVar("FABRIC8_DOCKER_REGISTRY_SERVICE_HOST", FABRIC8_DOCKER_REGISTRY);
    }
}
