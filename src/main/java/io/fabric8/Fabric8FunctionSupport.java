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
package io.fabric8;

import io.fabric8.utils.Strings;
import io.jenkins.functions.runtime.FunctionSupport;
import io.jenkins.functions.support.DefaultLogger;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * A useful base class for implementing functions reusing common semantics from pipeline libraries
 */
public class Fabric8FunctionSupport extends FunctionSupport {

    public Fabric8FunctionSupport() {
    }

    public Fabric8FunctionSupport(FunctionSupport parentStep) {
        this.logger = parentStep.getLogger();
        if (this.logger == null) {
            logger = DefaultLogger.getInstance();
        }
        this.currentDir = parentStep.getCurrentDir();
    }


    /**
     * Sends a message to Hubot
     */
    public void hubotSend(String message) {
        hubotSend(message, null, false);
    }

    /**
     * Sends a message to Hubot
     */
    public void hubotSend(String message, String room, boolean failOnError) {
        Map<String, Object> map = new LinkedHashMap<>(3);
        if (Strings.notEmpty(room)) {
            map.put("room", room);
        }
        map.put("message", message);
        map.put("failOnError", failOnError);
        callStep("hubotSend", map);
    }

}
