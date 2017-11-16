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

import io.fabric8.Fabric8Commands;
import io.fabric8.FunctionSupport;
import io.fabric8.Utils;
import io.fabric8.pipeline.steps.helpers.BooleanHelpers;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;
import org.apache.commons.beanutils.PropertyUtils;

import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Step(displayName = "Performs a Fabric8 Analytics scan")
public class BayesianScanner extends FunctionSupport implements Function<BayesianScanner.Arguments, String> {

    public BayesianScanner() {
    }

    public BayesianScanner(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public String apply(Arguments config) {
        final String serviceName = config.getServiceName();
        if (config.isRunBayesianScanner()) {
            Fabric8Commands flow = new Fabric8Commands(this);
            final Utils utils = new Utils(this);
            echo("Checking " + serviceName + " exists");
            if (flow.hasService(serviceName)) {
                try {
                    sh("mvn io.github.stackinfo:stackinfo-maven-plugin:0.2:prepare");
                    retry(3, new Callable<Object>() {
                        @Override
                        public Object call() throws Exception {
                            LinkedHashMap<String, Object> map = new LinkedHashMap<>(1);
                            map.put("url", "https://bayesian-link");
                            Object response = step("bayesianAnalysis", map);

                            Object success = PropertyUtils.getProperty(response, "success");
                            if (BooleanHelpers.asBoolean(success)) {
                                Object url = PropertyUtils.getProperty(response, "analysisUrl");
                                if (url != null) {
                                    return utils.addAnnotationToBuild("fabric8.io/bayesian.analysisUrl", url.toString());
                                }
                            } else {
                                error("Bayesian analysis failed " + response);
                            }
                            return null;
                        }
                    });
                } catch (Exception err) {
                    error("Unable to run Bayesian analysis", err);
                }

            } else {
                error("Code validation service: " + serviceName + " not available");
            }
        }
        return null;
    }

    public static class Arguments {
        @Argument
        private String serviceName = "bayesian-link";
        @Argument
        private boolean runBayesianScanner = true;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public boolean isRunBayesianScanner() {
            return runBayesianScanner;
        }

        public void setRunBayesianScanner(boolean runBayesianScanner) {
            this.runBayesianScanner = runBayesianScanner;
        }
    }

}
