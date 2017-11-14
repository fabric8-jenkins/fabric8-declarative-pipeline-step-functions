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
package io.fabric8.pipeline.steps.support;

import io.fabric8.pipeline.steps.git.GitHelper;
import io.fabric8.pipeline.steps.git.GitRepositoryInfo;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class GitCloneUrlParseTest {
    @Test
    public void testParseGitHostOrganisation() throws Exception {
        assertParseGitRepositoryInfo("git://host.xz/org/repo", "host.xz", "org", "repo");
        assertParseGitRepositoryInfo("git://host.xz/org/repo.git", "host.xz", "org", "repo");
        assertParseGitRepositoryInfo("git://host.xz/org/repo.git/", "host.xz", "org", "repo");
        assertParseGitRepositoryInfo("git://github.com/jstrachan/npm-pipeline-test-project.git", "github.com", "jstrachan", "npm-pipeline-test-project");
        assertParseGitRepositoryInfo("https://github.com/fabric8io/foo.git", "github.com", "fabric8io", "foo");
        assertParseGitRepositoryInfo("https://github.com/fabric8io/foo", "github.com", "fabric8io", "foo");
        assertParseGitRepositoryInfo("git@github.com:jstrachan/npm-pipeline-test-project.git", "github.com", "jstrachan", "npm-pipeline-test-project");
        assertParseGitRepositoryInfo("git@github.com:bar/foo.git", "github.com", "bar", "foo");
        assertParseGitRepositoryInfo("git@github.com:bar/foo", "github.com", "bar", "foo");
    }

    private void assertParseGitRepositoryInfo(String uri, String host, String organsation, String name) {
        GitRepositoryInfo actual = GitHelper.parseGitRepositoryInfo(uri);
        assertThat(actual).describedAs("Should have found GitRepositoryInfo").isNotNull();
        assertThat(actual.getHost()).describedAs("host").isEqualTo(host);
        assertThat(actual.getOrganisation()).describedAs("organsation").isEqualTo(organsation);
        assertThat(actual.getName()).describedAs("name").isEqualTo(name);
    }

}
