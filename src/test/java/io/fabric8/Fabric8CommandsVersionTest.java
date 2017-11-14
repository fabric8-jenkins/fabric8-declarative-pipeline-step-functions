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

import io.fabric8.support.Tests;
import io.fabric8.utils.IOHelpers;
import io.fabric8.utils.Strings;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class Fabric8CommandsVersionTest {
    protected File testWorkDir;

    @Before
    public void init() {
        testWorkDir = Tests.getCleanWorkDir(getClass());
    }

    @Test
    public void testVersions() throws Exception {
        assertVersion(new VersionTest("tag", "1.0.1", "1.0-SNAPSHOT", "git tag 1.0.0"));

        assertVersion(new VersionTest("no-tags-no-pom", "1.0.0", null));

        assertVersion(new VersionTest("tag-with-v", "1.0.1", "1.0-SNAPSHOT", "git tag v1.0.0"));
        assertVersion(new VersionTest("tag-with-v", "1.0.2", "1.0-SNAPSHOT", "git tag v1.0.0", "git tag v1.0.1"));
        assertVersion(new VersionTest("tag-with-v", "1.2.1", "1.0-SNAPSHOT", "git tag v1.0.0", "git tag v1.0.1", "git tag v1.2.0"));

        // pom upgrade tests
        assertVersion(new VersionTest("no-tags", "2.0.0", "2.0-SNAPSHOT"));
        assertVersion(new VersionTest("tag-with-pom-upgrade", "2.0.0", "2.0-SNAPSHOT", "git tag v1.0.0"));
    }

    protected void assertVersion(VersionTest test) throws IOException {
        File dir = new File(testWorkDir, test.getName());
        dir.mkdirs();

        Fabric8Commands step = new Fabric8Commands();
        step.setCurrentDir(dir);

        step.sh("git init");
        step.sh("echo Hello > ReadMe.md");
        step.sh("git add *.md");
        step.sh("git commit -m 'initial import'");

        String[] commands = test.getCommands();
        for (String command : commands) {
            step.sh(command);
        }

        String pomVersion = test.getPomVersion();
        if (Strings.notEmpty(pomVersion)) {
            // lets create a dummy pom.xml for the test
            String pomXml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                    "  <modelVersion>4.0.0</modelVersion>\n" +
                    "  <groupId>foo</groupId>\n" +
                    "  <artifactId>far</artifactId>\n" +
                    "  <version>" + pomVersion + "</version>\n" +
                    "</project>";
            IOHelpers.writeFully(new File(dir, "pom.xml"), pomXml);
        }

        String newVersion = step.getNewVersionFromTag(pomVersion);

        assertThat(newVersion).describedAs("New version for dir " + dir + " pomVersion " + pomVersion).isEqualTo(test.getExpectedVersion());
    }

    public static class VersionTest {
        private final String name;
        private final String expectedVersion;
        private final String pomVersion;
        private final String[] commands;

        public VersionTest(String name, String expectedVersion, String pomVersion, String... commands) {
            this.name = name;
            this.expectedVersion = expectedVersion;
            this.pomVersion = pomVersion;
            this.commands = commands;
        }

        public String getName() {
            return name;
        }

        public String getExpectedVersion() {
            return expectedVersion;
        }

        public String getPomVersion() {
            return pomVersion;
        }

        public String[] getCommands() {
            return commands;
        }
    }

}
