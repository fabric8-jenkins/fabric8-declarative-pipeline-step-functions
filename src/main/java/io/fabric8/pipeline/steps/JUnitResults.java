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
import io.jenkins.functions.Argument;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

public class JUnitResults extends FunctionSupport implements Function<JUnitResults.Arguments, String> {
    public JUnitResults() {
    }

    public JUnitResults(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    public String apply(Arguments config) {
        boolean archiveTestResults = config.isArchiveTestResults();
        if (archiveTestResults) {
            try {
                List<File> sureFireReports = findFiles("**/surefire-reports/*.xml");
                if (!sureFireReports.isEmpty()) {
                    LinkedHashMap<String, Serializable> map1 = new LinkedHashMap<>(3);
                    map1.put("$class", "JUnitResultArchiver");
                    map1.put("testResults", "**/surefire-reports/*.xml");
                    map1.put("healthScaleFactor", 1.0);
                    step("JUnitResultArchiver", map1);
                }

                List<File> failSafeReports = findFiles("**/failsafe-reports/*.xml");
                if (!failSafeReports.isEmpty()) {
                    LinkedHashMap<String, Serializable> map2 = new LinkedHashMap<String, Serializable>(3);
                    map2.put("$class", "JUnitResultArchiver");
                    map2.put("testResults", "**/failsafe-reports/*.xml");
                    map2.put("healthScaleFactor", 1.0);
                    step("JUnitResultArchiver", map2);
                }
            } catch (Exception err) {
                error("Failed to find test results", err);
            }
        }
        return null;
    }

    public static class Arguments {
        @Argument
        private boolean archiveTestResults = true;

        public boolean isArchiveTestResults() {
            return archiveTestResults;
        }

        public void setArchiveTestResults(boolean archiveTestResults) {
            this.archiveTestResults = archiveTestResults;
        }
    }

}
