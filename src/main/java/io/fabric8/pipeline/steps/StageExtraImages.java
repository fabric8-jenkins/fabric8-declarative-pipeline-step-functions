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
import io.fabric8.Fabric8FunctionSupport;
import io.jenkins.functions.runtime.FunctionSupport;
import io.fabric8.pipeline.steps.model.ServiceConstants;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;

import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Step(displayName = "Stages additional docker images")
public class StageExtraImages extends Fabric8FunctionSupport implements Function<StageExtraImages.Arguments, String> {
    public StageExtraImages() {
    }

    public StageExtraImages(FunctionSupport parentStep) {
        super(parentStep);
    }

    @Override
    @Step
    public String apply(final Arguments config) {
        final List<String> images = config.getImages();
        final String tag = config.getTag();

        final String registryHost = ServiceConstants.getDockerRegistryHost();
        final String registryPort = ServiceConstants.getDockerRegistryPort();

        if (Strings.isNullOrEmpty(tag) || images == null) {
            error("Missing arguments - was given: " + config);
            return null;
        }
        return container("docker", () -> {
            for (String image : images) {
                retry(3, () -> {
                    sh("docker pull docker.io/fabric8/" + image + ":latest");
                    sh("docker tag docker.io/fabric8/" + image + ":latest " + registryHost + ":" + registryPort + "/fabric8/" + image + ":" + tag);
                    sh("docker tag docker.io/fabric8/" + image + ":latest docker.io/fabric8/" + image + ":" + tag);
                    sh("docker push " + registryHost + ":" + registryPort + "/fabric8/" + image + ":" + tag);
                    return null;
                });
            }
            return null;
        });
    }

    public String apply(String tag, List<String> extraStageImages) {
        return apply(new Arguments(tag, extraStageImages));
    }

    public static class Arguments {
        @Argument
        @NotEmpty
        private String tag = "";
        @Argument
        private List<String> images = new ArrayList<>();

        public Arguments() {
        }

        public Arguments(String tag, List<String> images) {
            this.tag = tag;
            this.images = images;
        }

        @Override
        public String toString() {
            return "Arguments{" +
                    "tag='" + tag + '\'' +
                    ", images=" + images +
                    '}';
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public List<String> getImages() {
            return images;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }
    }
}
