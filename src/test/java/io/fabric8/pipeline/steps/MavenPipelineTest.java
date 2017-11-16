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

import io.fabric8.support.Tests;
import io.jenkins.functions.runtime.FunctionContext;
import io.jenkins.functions.runtime.StepFunctions;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class MavenPipelineTest {
    protected File testWorkDir;
    private FunctionContext functionContext;

    @Before
    public void init() {
        testWorkDir = Tests.getCleanWorkDir(getClass());
        functionContext = new FunctionContext();
        functionContext.setCurrentDir(testWorkDir);
    }

    @Test
    public void testMavenRelease() throws Exception {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("gitCloneUrl", "git@github.com:jstrachan/test-fabric8-declarative-step-functions-library.git");
        arguments.put("containerName", "maven-3.5");
        
        Object result = StepFunctions.invokeFunction("mavenPipeline", arguments, functionContext, MavenPipelineTest.class.getClassLoader());
        System.out.println("Result: " + result);
    }

}
