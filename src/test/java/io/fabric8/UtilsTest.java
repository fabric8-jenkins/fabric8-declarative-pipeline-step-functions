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
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class UtilsTest {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    protected Utils step = Tests.createUtils(getClass());

    @Test
    public void testRepoName() throws Exception {
        assertRepoName("foo", "foo");
        assertRepoName("foo/master", "foo");
        assertRepoName("organisation/foo/master", "foo");
    }

    protected void assertRepoName(String jobName, String expected) {
        environmentVariables.set("JOB_NAME", jobName);

        String actual = step.getRepoName();
        assertThat(actual).describedAs("repoName for jobName: " + jobName).isEqualTo(expected);

    }

}
