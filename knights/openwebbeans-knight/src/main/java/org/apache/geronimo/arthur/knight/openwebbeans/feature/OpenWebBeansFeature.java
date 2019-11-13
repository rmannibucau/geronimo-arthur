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
package org.apache.geronimo.arthur.knight.openwebbeans.feature;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.hosted.FeatureImpl;
import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.exception.ProxyGenerationException;
import org.apache.webbeans.proxy.InterceptorDecoratorProxyFactory;
import org.apache.webbeans.proxy.NormalScopeProxyFactory;
import org.apache.webbeans.proxy.SubclassProxyFactory;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
public class OpenWebBeansFeature implements Feature {
    public static final class Options {
        @Option(help = "OpenWebBeans BDA dump.", type = OptionType.User)
        static final HostedOptionKey<String> OpenWebBeansBda = new HostedOptionKey<>(null);

        @Option(help = "OpenWebBeans proxies mapping.", type = OptionType.User)
        static final HostedOptionKey<String> OpenWebBeansProxies = new HostedOptionKey<>(null);
    }

    // org.graalvm.compiler.options.processor is not on central
    public static class OpenWebBeansOptions implements OptionDescriptors {
        @Override
        public OptionDescriptor get(final String value) {
            switch (value) {
                case "OpenWebBeansBda":
                    return OptionDescriptor.create(
                            value, OptionType.User, String.class,
                            "OpenWebBeans BDA dump.",
                            Options.class, value,
                            Options.OpenWebBeansBda);
                case "OpenWebBeansProxies":
                    return OptionDescriptor.create(
                            value, OptionType.User, String.class,
                            "OpenWebBeans proxies mapping.",
                            Options.class, value,
                            Options.OpenWebBeansProxies);
                default:
                    return null;
            }
        }

        @Override
        public Iterator<OptionDescriptor> iterator() {
            return Stream.of("OpenWebBeansBda", "OpenWebBeansProxies").map(this::get).iterator();
        }
    }

    @Override
    public void beforeAnalysis(final BeforeAnalysisAccess access) {
        if (Options.OpenWebBeansBda.hasBeenSet()) {
            register(Options.OpenWebBeansBda, "ARTHUR-INF/knight/openwebbeans/OpenWebBeansBda.json");
        }
    }

    @Override
    public void duringSetup(final DuringSetupAccess a) {
        if (Options.OpenWebBeansProxies.hasBeenSet()) {
            final Proxies store = new Proxies();
            ImageSingletons.add(Proxies.class, store);

            try (final Reader reader = new InputStreamReader(Files.newInputStream(Paths.get(Options.OpenWebBeansProxies.getValue())))) {
                final Object proxies = new JSONParser(reader).parse();
                final List<Object> defs = List.class.cast(proxies);
                defs.stream().map(Map.class::cast).forEach(it -> register(
                        store,
                        load(FeatureImpl.DuringSetupAccessImpl.class.cast(a).getImageClassLoader().getClassLoader(), it)));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Class<?> load(final ClassLoader loader, final Map<String, Object> it) {
        final String proxyClassName = String.valueOf(it.get("class"));
        final Class classToProxy;
        try {
            classToProxy = loader.loadClass(String.valueOf(it.get("proxiedClass")));
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        // todo: actually save the methods and constructor in the json in OpenWebBeansExtension based on Bean<?> intereptorInfo
        final Method[] interceptedMethods = classToProxy.getDeclaredMethods();
        final Method[] nonInterceptedMethods = new Method[0];
        final Constructor<?> constructor;
        try {
            constructor = classToProxy.getDeclaredConstructor();
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }

        if (proxyClassName.endsWith("$$OwbNormalScopeProxy$$OwbInterceptProxy")) {
            final Class normalScopeProxyClass = new NormalFactory().createProxyClass(
                    loader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
            return new InterceptFactory().createProxyClass(
                    loader, proxyClassName, normalScopeProxyClass, interceptedMethods, nonInterceptedMethods, constructor);
        }
        if (proxyClassName.endsWith("$$OwbNormalScopeProxy")) {
            return new NormalFactory().createProxyClass(
                    loader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
        if (proxyClassName.endsWith("$$OwbSubClass")) {
            return new SubClassFactory().createProxyClass(
                    loader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
        if (proxyClassName.endsWith("$$OwbInterceptProxy")) {
            return new InterceptFactory().createProxyClass(
                    loader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
        throw new IllegalArgumentException("Unsupported proxy kind for " + proxyClassName);
    }

    private void register(final HostedOptionKey<String> value, final String resource) {
        try (final InputStream stream = Files.newInputStream(Paths.get(value.getValue()))) {
            Resources.registerResource(resource, stream);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void register(final Proxies store, final Class<?> loaded) {
        store.proxies.put(loaded.getName(), loaded);
    }

    private static class NormalFactory extends NormalScopeProxyFactory {
        private NormalFactory() {
            super(new WebBeansContext());
        }

        @Override // just give visibility from the enclosing target
        protected <T> Class<T> createProxyClass(final ClassLoader classLoader, final String proxyClassName,
                                                final Class<T> classToProxy, final Method[] interceptedMethods,
                                                final Method[] nonInterceptedMethods, final Constructor<T> constructor)
                throws ProxyGenerationException {
            return super.createProxyClass(classLoader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
    }

    private static class SubClassFactory extends SubclassProxyFactory {
        private SubClassFactory() {
            super(new WebBeansContext());
        }

        @Override // just give visibility from the enclosing target
        protected <T> Class<T> createProxyClass(final ClassLoader classLoader, final String proxyClassName,
                                                final Class<T> classToProxy, final Method[] interceptedMethods,
                                                final Method[] nonInterceptedMethods, final Constructor<T> constructor)
                throws ProxyGenerationException {
            return super.createProxyClass(classLoader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
    }

    private static class InterceptFactory extends InterceptorDecoratorProxyFactory {
        private InterceptFactory() {
            super(new WebBeansContext());
        }

        @Override // just give visibility from the enclosing target
        protected <T> Class<T> createProxyClass(final ClassLoader classLoader, final String proxyClassName,
                                                final Class<T> classToProxy, final Method[] interceptedMethods,
                                                final Method[] nonInterceptedMethods, final Constructor<T> constructor)
                throws ProxyGenerationException {
            return super.createProxyClass(classLoader, proxyClassName, classToProxy, interceptedMethods, nonInterceptedMethods, constructor);
        }
    }
}
