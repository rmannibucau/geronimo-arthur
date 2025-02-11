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
package org.apache.geronimo.arthur.api;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(TYPE)
@Retention(RUNTIME)
public @interface RegisterClass {
    boolean allDeclaredConstructors() default false;

    boolean allPublicConstructors() default false;

    boolean allDeclaredMethods() default false;

    boolean allPublicMethods() default false;

    boolean allDeclaredClasses() default false;

    boolean allPublicClasses() default false;

    boolean allDeclaredFields() default false;

    boolean allPublicFields() default false;

    /**
     * @return alias for allDeclared*.
     */
    boolean all() default false;
}
