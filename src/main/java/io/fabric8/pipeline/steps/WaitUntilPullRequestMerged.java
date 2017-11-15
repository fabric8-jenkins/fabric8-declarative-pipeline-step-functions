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

import com.google.common.base.Strings;
import io.fabric8.Fabric8Commands;
import io.fabric8.FunctionSupport;
import io.fabric8.pipeline.steps.git.GitHelper;
import io.fabric8.pipeline.steps.helpers.FailedBuildException;
import io.jenkins.functions.Argument;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class WaitUntilPullRequestMerged extends FunctionSupport implements Function<WaitUntilPullRequestMerged.Arguments, Boolean> {
    public WaitUntilPullRequestMerged() {
    }

    public WaitUntilPullRequestMerged(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    public Boolean apply(Arguments config) {
        Fabric8Commands flow = new Fabric8Commands(this);

        final GitHub gitHub = flow.createGitHub();

        final String project = config.getProject();
        final int prId = config.getId();

        if (prId <= 0 || Strings.isNullOrEmpty(project)) {
            echo("Missing arguments. Was given " + config);
            return false;
        }
        final String id = "" + prId;

        final String repoName = GitHelper.getRepoName(project);

        echo("Waiting for Pull Request " + prId + " on project " + project);

        final AtomicBoolean notified = new AtomicBoolean(false);

        // wait until the PR is merged, if there's a merge conflict the notify and wait until PR is finally merged
        return waitUntil(() -> {
            GHRepository repository = null;
            try {
                repository = gitHub.getRepository(project);
            } catch (Exception e) {
                throw new FailedBuildException("Could not find repository " + project, e);
            }

            GHPullRequest pullRequest = repository.getPullRequest(prId);
            if (pullRequest != null) {
                if (pullRequest.isMerged()) {
                    echo("Pull Request " + pullRequest.getHtmlUrl() + " is merged");
                    return true;
                }
                GHIssueState state = pullRequest.getState();
                if (state.equals(GHIssueState.CLOSED)) {
                    echo("Pull Request " + pullRequest.getHtmlUrl() + " is closed");
                    return true;
                }

                String branch = "master";
                GHCommitPointer head = pullRequest.getHead();
                if (head != null) {
                    branch = head.getRef();
                }
                if ("failure".equalsIgnoreCase(pullRequest.getMergeableState())) {
                    if (notified.compareAndSet(false, true)) {
                        String message = "Pull request was not automatically merged.  Please fix and update Pull Request to continue with release...\n" +
                                "\n" +
                                "git clone git@github.com:" + project + ".git\n" +
                                "cd " + repoName + "\n" +
                                "git fetch origin pull/" + id + "/head:fixPR" + id + "\n" +
                                "git checkout fixPR" + id + "\n" +
                                "\n" +
                                "  [resolve issue]\n" +
                                "\n" +
                                "git commit -a -m 'resolved merge issues caused by release dependency updates'\n" +
                                "git push origin fixPR" + id + ":" + branch + "\n";

                        echo(message);
                        hubotSend(message);


                        boolean shouldWeWait = requestResolve();

                        if (!shouldWeWait) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });
    }

    public boolean requestResolve() {
        String proceedMessage = "\nWould you like do resolve the conflict?  If so please reply with the proceed command.\n\nAlternatively you can skip this conflict.  This is highly discouraged but maybe necessary if we have a problem quickstart for example.\nTo do this chose the abort option below, note this particular action will not abort the release and only skip this conflict.\n";
        try {
            LinkedHashMap<String, Serializable> map = new LinkedHashMap<>(2);
            map.put("message", proceedMessage);
            map.put("failOnError", false);
            step("hubotApprove", map);
            return true;
        } catch (Exception err) {
            echo("Skipping conflict");
            return false;
        }
    }

    public static class Arguments {
        @Argument
        private int id;
        @Argument
        private String project;

        public Arguments() {
        }

        public Arguments(int id, String project) {
            this.id = id;
            this.project = project;
        }

        @Override
        public String toString() {
            return "Arguments{" +
                    "id=" + id +
                    ", project='" + project + '\'' +
                    '}';
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }
    }


}
