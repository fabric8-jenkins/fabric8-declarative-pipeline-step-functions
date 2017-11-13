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
package io.fabric8;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class Fabric8CommandsTest {
    protected Fabric8Commands step = new Fabric8Commands();
    protected String mavenCentral = "http://central.maven.org/maven2";

    @Test
    public void testGetMavenCentralVersion() throws Exception {
        String version = assertGetMavenCentralVersion("io/fabric8/kubernetes-client");

        String groupId = "io.fabric8";
        String artifactId = "kubernetes-client";
        String ext= "jar";
        assertArtifactInRepo(mavenCentral, groupId, artifactId, version, ext);
    }

    @Test
    public void testGetVersion() throws Exception {
        assertGetVersion(mavenCentral, "io/fabric8/kubernetes-client");
    }

    @Test
    public void testGetReleaseVersion() throws Exception {
        assertGetReleaseVersion("io/fabric8/kubernetes-client");
    }

    public void assertArtifactInRepo(String repo, String groupId, String artifactId, String version, String ext) {
        boolean result = step.isArtifactAvailableInRepo(repo, groupId, artifactId, version, ext);
        assertThat(result).describedAs("Expected artifact in repository: " + repo + ", groupId: " + groupId
                + ", artifactId: " + artifactId + ", version: " + version + ", ext: " + ext).isTrue();
    }


    public  String assertGetVersion(String repo, String artifact) {
        String version = step.getVersion(repo, artifact);

        System.out.println("latest " + repo + " version of " + artifact + " is " + version);
        assertThat(version).describedAs(repo + " version of artifact " + artifact).isNotEmpty();
        return version;
    }
    
    public  String assertGetMavenCentralVersion(String artifact) {
        String version = step.getMavenCentralVersion(artifact);

        System.out.println("latest maven central version of " + artifact + " is " + version);
        assertThat(version).describedAs("maven central version of artifact " + artifact).isNotEmpty();
        return version;
    }

    public  String assertGetReleaseVersion(String artifact) {
        String version = step.getReleaseVersion(artifact);

        System.out.println("latest release version of " + artifact + " is " + version);
        assertThat(version).describedAs("release version of artifact " + artifact).isNotEmpty();
        return version;
    }
}
