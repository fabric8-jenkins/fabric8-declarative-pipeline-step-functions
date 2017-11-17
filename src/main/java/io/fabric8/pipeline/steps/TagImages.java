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
import io.fabric8.FunctionSupport;
import io.fabric8.pipeline.steps.model.ServiceConstants;
import io.jenkins.functions.Argument;
import io.jenkins.functions.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Tags docker images
 */
@Step(displayName = "Tags docker images")
public class TagImages extends FunctionSupport implements Function<TagImages.Arguments, String> {

    public TagImages() {
    }

    public TagImages(FunctionSupport parentStep) {
        super(parentStep);
    }

    public String apply(String tag, String... images) {
        return apply(tag, Arrays.asList(images));
    }

    public String apply(String tag, List<String> images) {
        return apply(new Arguments(tag, images));
    }

    @Override
    @Step
    public String apply(final Arguments args) {
        final List<String> images = args.getImages();
        final String tag = args.getTag();
        if (Strings.isNullOrEmpty(tag)) {
            error("No tag specified for tagImages step for images " + images);
            return null;
        }

        return container("docker", () -> {
            for (String image : images) {
                retry(3, () -> {
                    String registryHost = ServiceConstants.getDockerRegistryHost();
                    String registryPort = ServiceConstants.getDockerRegistryPort();

                    sh("docker pull " + registryHost + ":" + registryPort + "/fabric8/" + image + ":" + tag);
                    sh("docker tag  " + registryHost + ":" + registryPort + "/fabric8/" + image + ":" + tag + " docker.io/fabric8/" + image + ":" + tag);
                    sh("docker push docker.io/fabric8/" + image + ":" + tag);
                    return null;
                });
            }
            return null;
        });
    }

    public static class Arguments {
        @Argument
        private String tag = "";
        @Argument
        private List<String> images = new ArrayList<>();

        public Arguments() {
        }

        public Arguments(String tag, List<String> images) {
            this.tag = tag;
            this.images = images;
        }

        public List<String> getImages() {
            return images;
        }

        public void setImages(List<String> images) {
            this.images = images;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }
    }
}
