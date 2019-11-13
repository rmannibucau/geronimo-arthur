/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.arthur.integrationtests;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;

public final class CdiMain {
    private CdiMain() {
        // noop
    }

    // debug command line:
    // $ ./target/owb.graal.bin -Dorg.apache.geronimo.arthur.integrationtests.CdiMain.log.level=INFO
    public static void main(final String[] args) {
        // for the IT we don't want startup logs to assert the output easily in the test
        Logger.getLogger("org.apache.webbeans").setLevel(
                Level.parse(System.getProperty(CdiMain.class.getName() + ".log.level", "WARNING")));

        try (final SeContainer container = SeContainerInitializer.newInstance().initialize()) {
            //no-op
        }
    }
}
