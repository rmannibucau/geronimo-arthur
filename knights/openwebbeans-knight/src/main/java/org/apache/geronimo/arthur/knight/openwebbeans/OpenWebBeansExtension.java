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
package org.apache.geronimo.arthur.knight.openwebbeans;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.enterprise.context.NormalScope;
import javax.interceptor.InterceptorBinding;

import org.apache.geronimo.arthur.knight.openwebbeans.feature.Proxies;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.openwebbeans.se.CDISeScannerService;
import org.apache.webbeans.corespi.scanner.xbean.OwbAnnotationFinder;
import org.apache.webbeans.spi.BdaScannerService;
import org.apache.webbeans.spi.BeanArchiveService;
import org.apache.xbean.finder.AnnotationFinder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenWebBeansExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        final Path workDir = Paths.get(requireNonNull(context.getProperty("workingDirectory"), "workingDirectory property"));

        final CDISeScannerService scanner = new CDISeScannerService();
        scanner.loader(Thread.currentThread().getContextClassLoader());
        try {
            scanner.scan();

            context.addNativeImageOption("-H:OpenWebBeansBda=" + dump(workDir, "openwebbeans.bda.properties", toBdas(scanner)));
            context.addNativeImageOption("-H:OpenWebBeansProxies=" + dump(workDir, "openwebbeans.proxies.properties", toProxies(scanner.getFinder(), context)));
            context.initializeAtBuildTime(Proxies.class.getName());
            registerReflectionForInterceptors(scanner.getFinder(), context);
        } finally {
            scanner.release();
        }
    }

    private void registerReflectionForInterceptors(final OwbAnnotationFinder finder, final Context context) {
        finder.getAnnotatedClassNames().stream()
                .map(finder::getClassInfo)
                .filter(this::isInterceptor)
                .forEach(classInfo -> registerReflection(context, classInfo));
    }

    private String toProxies(final OwbAnnotationFinder finder, final Context context) {
        return finder.getAnnotatedClassNames().stream()
                .map(finder::getClassInfo)
                .filter(this::isNormalScoped)
                .peek(classInfo -> registerReflection(context, classInfo))
                // todo: refine naming (see Target_AbstractProxyFactory$DeterministicNaming too) and don't generate blindy potential proxies
                .flatMap(classInfo -> forProxySuffixes().map(suffix -> {
                    final StringBuilder builder = new StringBuilder("{");
                    builder.append("\"class\":\"").append(classInfo.getName()).append(suffix).append("\",");
                    builder.append("\"proxiedClass\":\"").append(classInfo.getName()).append("\"");
                    builder.append("}");
                    registerProxyReflectionModel(context, classInfo, suffix);
                    return builder;
                }))
                .collect(joining(",", "[", "]"));
    }

    private Stream<String> forProxySuffixes() {
        return Stream.of("$$OwbNormalScopeProxy", "$$OwbInterceptProxy", "$$OwbNormalScopeProxy$$OwbInterceptProxy");
    }

    private void registerReflection(final Context context, final AnnotationFinder.ClassInfo classInfo) {
        try {
            Class<?> current = classInfo.get();
            while (current != null && current != Object.class) {
                final ClassReflectionModel model = new ClassReflectionModel();
                model.setName(current.getName());
                model.setAllDeclaredFields(true);
                model.setAllDeclaredMethods(true);
                model.setAllDeclaredConstructors(true);
                context.register(model);

                current = current.getSuperclass();
            }
        } catch (final ClassNotFoundException e) {
            // no-op
        }
    }

    private void registerProxyReflectionModel(final Context context, final AnnotationFinder.ClassInfo classInfo, final String proxyNameSuffix) {
        final ClassReflectionModel model = new ClassReflectionModel();
        model.setName(classInfo.getName() + proxyNameSuffix);
        model.setAllDeclaredMethods(true);
        model.setAllDeclaredFields(true);
        context.register(model);
    }

    private boolean isNormalScoped(final AnnotationFinder.ClassInfo classInfo) {
        return hasMetaAnnotation(classInfo, NormalScope.class);
    }

    private boolean isInterceptor(final AnnotationFinder.ClassInfo classInfo) {
        return hasMetaAnnotation(classInfo, InterceptorBinding.class);
    }

    private boolean hasMetaAnnotation(final AnnotationFinder.ClassInfo clazz, final Class<? extends Annotation> marker) {
        try {
            return Stream.of(clazz.get().getAnnotations())
                    .anyMatch(annotation -> Stream.of(annotation.annotationType().getAnnotations())
                            .anyMatch(a -> marker.getName().equals(a.annotationType().getName())));
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private String toBdas(final BdaScannerService scanner) {
        return scanner.getBeanClassesPerBda().entrySet().stream()
                .map(this::toJson)
                .collect(joining(",", "[", "]"));
    }

    private String toJson(final Map.Entry<BeanArchiveService.BeanArchiveInformation, Set<Class<?>>> e) {
        final BeanArchiveService.BeanArchiveInformation bai = e.getKey();
        final StringBuilder builder = new StringBuilder();
        builder.append("{");
        builder.append("\"beanDiscoveryMode\":")
                .append(ofNullable(bai.getBeanDiscoveryMode()).map(it -> "\"" + it + '"').orElse("null"))
                .append(",");
        builder.append("\"version\":")
                .append(ofNullable(bai.getVersion())
                        .map(String::trim)
                        .filter(it -> !it.isEmpty())
                        .map(it -> "\"" + it + '"')
                        .orElse("null"))
                .append(",");
        builder.append("\"excludedClasses\":[").append(toJsonList(bai.getExcludedClasses(), bai)).append("],");
        builder.append("\"excludedPackages\":[").append(toJsonList(bai.getExcludedPackages(), bai)).append("],");
        builder.append("\"interceptors\":[").append(toJsonList(bai.getInterceptors(), bai)).append("],");
        builder.append("\"decorators\":[").append(toJsonList(bai.getDecorators(), bai)).append("],");
        builder.append("\"alternativeClasses\":[").append(toJsonList(bai.getAlternativeClasses(), bai)).append("],");
        builder.append("\"alternativeStereotypes\":[").append(toJsonList(bai.getAlternativeStereotypes(), bai)).append("],");
        builder.append("\"classes\":[").append(toJsonList(e.getValue().stream().map(Class::getName).collect(toList()), bai)).append("]");
        builder.append("}");
        return builder.toString();
    }

    private String toJsonList(final List<String> excludedClasses, final BeanArchiveService.BeanArchiveInformation bai) {
        return ofNullable(excludedClasses)
                .map(it -> it.stream().map(v -> "\"" + v + "\"").collect(joining(",")))
                .orElse("");
    }

    private String dump(final Path workDir, final String name, final String data) {
        if (!java.nio.file.Files.isDirectory(workDir)) {
            try {
                java.nio.file.Files.createDirectories(workDir);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }
        final Path out = workDir.resolve(name);
        try (final BufferedWriter writer = Files.newBufferedWriter(out)) {
            writer.write(data);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
        log.info("Created '{}'", out);
        return out.toAbsolutePath().toString();
    }
}
