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

import java.util.function.Function;

public class ContentRepository extends FunctionSupport implements Function<ContentRepository.Arguments, String> {
    public ContentRepository() {
    }

    public ContentRepository(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    public String apply(Arguments config) {
        final String serviceName = config.getServiceName();
        if (config.isUseContentRepository()) {
            Fabric8Commands flow = new Fabric8Commands();
            echo("Checking " + serviceName + " exists");
            if (flow.hasService(serviceName)) {
                try {
                    //sh 'mvn site site:deploy'
                    echo("mvn site disabled");
                } catch (Exception err) {
                    // lets carry on as maven site isn't critical
                    echo("unable to generate maven site");
                }

            } else {
                echo("no content-repository service so not deploying the maven site report");
            }


        }
        return null;
    }

    public static class Arguments {
        private String serviceName = "content-repository";
        private boolean useContentRepository = true;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public boolean isUseContentRepository() {
            return useContentRepository;
        }

        public void setUseContentRepository(boolean useContentRepository) {
            this.useContentRepository = useContentRepository;
        }
    }
}
