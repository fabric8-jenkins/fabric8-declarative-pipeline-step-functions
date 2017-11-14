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
import io.fabric8.support.Tests;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Callable;

/**
 */
public class MavenReleaseTest {
    protected File testWorkDir;

    @Before
    public void init() {
        testWorkDir = Tests.getCleanWorkDir(getClass());
    }

    @Test
    public void testMavenRelease() throws Exception {
        FunctionSupport test = new FunctionSupport();
        test.setCurrentDir(testWorkDir);
        test.git("git@github.com:fabric8io/pipeline-test-project-dependency.git", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                MavenRelease mvn = new MavenRelease(test);
                MavenRelease.Arguments args = new MavenRelease.Arguments();
                return mvn.apply(args);
            }
        });
    }

}
