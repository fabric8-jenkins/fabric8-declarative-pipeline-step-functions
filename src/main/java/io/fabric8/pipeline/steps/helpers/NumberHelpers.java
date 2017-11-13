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

/**
 */
public class NumberHelpers {
    public static boolean isInteger(String text) {
        String value = text.trim();
        boolean answer = false;
        for (int i = 0, size = value.length(); i < size; i++) {
            if (Character.isDigit(value.charAt(i))) {
                answer = true;
            } else {
                return false;
            }
        }
        return answer;
    }
}
