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
package org.apache.geronimo.arthur.knight.openwebbeans.subtitution;

import java.lang.reflect.Method;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.apache.geronimo.arthur.knight.openwebbeans.feature.Proxies;
import org.apache.webbeans.exception.ProxyGenerationException;
import org.apache.webbeans.proxy.AbstractProxyFactory;
import org.graalvm.nativeimage.ImageSingletons;

@TargetClass(AbstractProxyFactory.class)
public final class Target_AbstractProxyFactory {
    @Substitute
    protected ClassLoader getProxyClassLoader(final Class<?> beanClass) {
        return Thread.currentThread().getContextClassLoader();
    }

    @Substitute
    protected String getUnusedProxyClassName(final ClassLoader classLoader, final String proxyClassName) {
        return DeterministicNaming.getProxyName(proxyClassName);
    }

    @Substitute
    protected <T> Class<T> createProxyClass(final ClassLoader classLoader, final String proxyClassName,
                                            final Class<T> classToProxy,
                                            final Method[] interceptedMethods, final Method[] nonInterceptedMethods)
            throws ProxyGenerationException {
        System.out.println(">> " + proxyClassName);
        return (Class<T>) ImageSingletons.lookup(Proxies.class).proxies.get(proxyClassName);
    }

    static class DeterministicNaming { // todo: support multiple proxy names
        public static String getProxyName(final String proxyClassName) {
            if (proxyClassName.startsWith("java")) {
                return "org.apache.webbeans.custom." + proxyClassName;
            }
            return proxyClassName;
        }
    }
}
