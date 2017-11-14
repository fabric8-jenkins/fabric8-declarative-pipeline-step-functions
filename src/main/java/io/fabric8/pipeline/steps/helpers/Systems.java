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
package io.fabric8.pipeline.steps.helpers;

import io.fabric8.utils.Strings;

/**
 */
public class Systems {
    /**
     * Returns the environment variable or returns the default
     */
    public static String getEnvVar(String name, String defaultValue) {
        String answer = System.getenv(name);
        if (Strings.notEmpty(answer)) {
            return answer;
        }
        return defaultValue;
    }
}
