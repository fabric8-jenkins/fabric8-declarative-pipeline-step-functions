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
import org.kohsuke.github.GHIssueComment;

import java.util.Arrays;
import java.util.List;

import static io.fabric8.Fabric8Commands.hasGitHubEnvVars;
import static org.assertj.core.api.Assertions.assertThat;

/**
 */
public class Fabric8CommandsIssueCommentsTest {
    Fabric8Commands step = new Fabric8Commands();

    @Test
    public void testIssueComments() throws Exception {
        if (!hasGitHubEnvVars()) {
            System.out.println("Disabling test as no GITHUB env vars " + Arrays.asList(EnvironmentVariableNames.GITHUB_USER, EnvironmentVariableNames.GITHUB_PASSWORD, EnvironmentVariableNames.GITHUB_TOKEN));
            return;
        }
        String project = "fabric8-updatebot/updatebot";
        int issueNumber = 15;
        List<GHIssueComment> comments = step.getIssueComments(project, issueNumber);
        assertThat(comments).describedAs("Issue comments for project " + project + " issue " + issueNumber).isNotEmpty();
        for (GHIssueComment comment : comments) {
            System.out.println("@" + comment.getUser().getLogin() + ": "+ comment.getBody());
        }

    }

}
