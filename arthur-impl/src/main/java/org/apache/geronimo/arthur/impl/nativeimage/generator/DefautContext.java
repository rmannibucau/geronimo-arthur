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
package org.apache.geronimo.arthur.impl.nativeimage.generator;

import static java.util.Arrays.asList;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.geronimo.arthur.impl.nativeimage.ArthurNativeImageConfiguration;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.DynamicProxyModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import lombok.Data;

@Data
public class DefautContext implements ArthurExtension.Context {
    private final ArthurNativeImageConfiguration configuration;
    private final Function<Class<? extends Annotation>, Collection<Class<?>>> annotatedClassesFinder;
    private final Function<Class<? extends Annotation>, Collection<Method>> methodFinder;
    private final Function<Class<?>, Collection<Class<?>>> implementationFinder;
    private final Collection<ClassReflectionModel> reflections = new HashSet<>();
    private final Collection<ResourceModel> resources = new HashSet<>();
    private final Collection<ResourceBundleModel> bundles = new HashSet<>();
    private final Collection<DynamicProxyModel> dynamicProxyModels = new HashSet<>();
    private final Map<String, String> extensionProperties;
    private boolean modified;

    @Override
    public <T extends Annotation> Collection<Class<?>> findAnnotatedClasses(final Class<T> annotation) {
        return annotatedClassesFinder.apply(annotation);
    }

    @Override
    public <T extends Annotation> Collection<Method> findAnnotatedMethods(final Class<T> annotation) {
        return methodFinder.apply(annotation);
    }

    @Override
    public <T> Collection<Class<? extends T>> findImplementations(final Class<T> parent) {
        return Collection.class.cast(implementationFinder.apply(parent));
    }

    @Override
    public void register(final ClassReflectionModel classReflectionModel) {
        reflections.removeIf(it -> Objects.equals(classReflectionModel.getName(), it.getName()));
        reflections.add(classReflectionModel);
        modified = true;
    }

    @Override
    public void register(final ResourceModel resourceModel) {
        resources.add(resourceModel);
        modified = true;
    }

    @Override
    public void register(final ResourceBundleModel resourceBundleModel) {
        bundles.removeIf(it -> Objects.equals(it.getName(), resourceBundleModel.getName()));
        bundles.add(resourceBundleModel);
        modified = true;
    }

    @Override
    public void register(final DynamicProxyModel dynamicProxyModel) {
        if (dynamicProxyModels.add(dynamicProxyModel)) {
            modified = true;
        }
    }

    @Override
    public void enableAllSecurityServices() {
        configuration.setEnableAllSecurityServices(true);
    }

    @Override
    public void enableAllCharsets() {
        configuration.setAddAllCharsets(true);
    }

    @Override
    public void initializeAtBuildTime(final String... classes) {
        if (configuration.getInitializeAtBuildTime() == null) {
            configuration.setInitializeAtBuildTime(new ArrayList<>());
        }
        configuration.getInitializeAtBuildTime().addAll(asList(classes));
    }

    @Override
    public String getProperty(final String key) {
        return extensionProperties.get(key);
    }

    @Override
    public void setProperty(final String key, final String value) {
        extensionProperties.put(key, value);
    }

    @Override
    public void addNativeImageOption(final String option) {
        if (configuration.getCustomOptions() == null) {
            configuration.setCustomOptions(new ArrayList<>());
        }
        configuration.getCustomOptions().add(option);
    }

    @Override
    public Class<?> loadClass(final String name) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(name);
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Stream<Class<?>> findHierarchy(final Class<?> clazz) {
        return doFindHierarchy(clazz, new HashSet<>());
    }

    @Override
    public Optional<Predicate<String>> createPredicate(final String property, final ArthurExtension.PredicateType type) {
        return ofNullable(getProperty(property)).flatMap(ex -> Stream.of(ex.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .map(it -> of((Predicate<String>) n -> type.test(it, n)))
                .reduce(Optional.<Predicate<String>>empty(),
                        (opt, p) -> opt.map(e -> of(e.or(p.orElseThrow(IllegalArgumentException::new)))).orElse(p)));
    }

    @Override
    public Predicate<String> createIncludesExcludes(final String propertyBase, final ArthurExtension.PredicateType type) {
        final Optional<Predicate<String>> includes = createPredicate(propertyBase + "includes", type);
        final Optional<Predicate<String>> excludes = createPredicate(propertyBase + "excludes", type);
        return n -> {
            if (includes.isPresent()) {
                if (includes.orElseThrow(IllegalStateException::new).test(n)) {
                    return true;
                }
            }
            if (excludes.isPresent()) {
                if (excludes.orElseThrow(IllegalStateException::new).test(n)) {
                    return false;
                }
            }
            return !excludes.isPresent() && !includes.isPresent();
        };
    }

    @Override
    public <T> T unwrap(final Class<T> type) {
        if (ArthurNativeImageConfiguration.class == type) {
            return type.cast(configuration);
        }
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        throw new IllegalArgumentException("Unsupported unwrapping: " + type);
    }

    public void addReflectionConfigFile(final String path) {
        if (configuration.getReflectionConfigurationFiles() == null) {
            configuration.setReflectionConfigurationFiles(new ArrayList<>());
        }
        configuration.getReflectionConfigurationFiles().add(path);
    }

    public void addResourcesConfigFile(final String path) {
        if (configuration.getResourcesConfigurationFiles() == null) {
            configuration.setResourcesConfigurationFiles(new ArrayList<>());
        }
        configuration.getResourcesConfigurationFiles().add(path);
    }

    public void addDynamicProxiesConfigFile(final String path) {
        if (configuration.getDynamicProxyConfigurationFiles() == null) {
            configuration.setDynamicProxyConfigurationFiles(new ArrayList<>());
        }
        configuration.getDynamicProxyConfigurationFiles().add(path);
    }

    private Stream<Class<?>> doFindHierarchy(final Class<?> clazz, final Set<Class<?>> visited) {
        visited.add(clazz);
        return Stream.concat(Stream.concat(
                Stream.of(clazz), Stream.of(clazz.getSuperclass())), Stream.of(clazz.getInterfaces()))
                .filter(it -> Object.class != it && it != null)
                .flatMap(it -> visited.contains(it) ? Stream.of(it) : doFindHierarchy(it, visited))
                .distinct();
    }
}
