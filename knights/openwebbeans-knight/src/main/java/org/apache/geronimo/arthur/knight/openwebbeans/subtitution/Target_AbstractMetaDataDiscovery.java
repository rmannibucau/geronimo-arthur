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

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.json.JSONParser;
import org.apache.webbeans.corespi.scanner.AbstractMetaDataDiscovery;
import org.apache.webbeans.spi.BeanArchiveService;
import org.apache.webbeans.xml.DefaultBeanArchiveInformation;

@TargetClass(AbstractMetaDataDiscovery.class)
public final class Target_AbstractMetaDataDiscovery {
    @Substitute
    public Map<BeanArchiveService.BeanArchiveInformation, Set<Class<?>>> getBeanClassesPerBda() {
        return LoadingLogic.readBda(Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("ARTHUR-INF/knight/openwebbeans/OpenWebBeansBda.json"));
    }

    private static final class LoadingLogic {
        private LoadingLogic() {
            // no-op
        }

        private static Map<BeanArchiveService.BeanArchiveInformation, Set<Class<?>>> readBda(final InputStream stream) {
            try (final Reader reader = new InputStreamReader(stream)) {
                final List<Object> json = List.class.cast(new JSONParser(reader).parse());
                return json.stream().map(it -> (Map<String, Object>) it).collect(toMap(LoadingLogic::toBai, LoadingLogic::toClasses));
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private static BeanArchiveService.BeanArchiveInformation toBai(final Map<String, Object> t) {
            final DefaultBeanArchiveInformation bai = new DefaultBeanArchiveInformation(String.valueOf(t.get("url")));
            bai.setBeanDiscoveryMode(BeanArchiveService.BeanDiscoveryMode.valueOf(getString(t, "beanDiscoveryMode").orElse("NONE")));
            bai.setVersion(getString(t, "version").orElse(null));
            bai.setExcludedClasses(getStringList(t, "excludedClasses"));
            bai.setExcludedPackages(getStringList(t, "excludedPackages"));
            bai.setInterceptors(getStringList(t, "interceptors"));
            bai.setDecorators(getStringList(t, "decorators"));
            bai.getAlternativeClasses().addAll(getStringList(t, "alternativeClasses"));
            bai.getAlternativeStereotypes().addAll(getStringList(t, "alternativeStereotypes"));
            return bai;
        }

        private static Set<Class<?>> toClasses(final Map<String, Object> t) {
            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
            return ofNullable(t.get("classes"))
                    .map(classes -> load(loader, (Collection<String>) classes))
                    .orElseGet(Collections::emptySet);
        }

        private static Set<Class<?>> load(final ClassLoader loader, final Collection<String> classes) {
            final Set<Class<?>> out = new HashSet<>();
            for (final String c : classes) {
                out.add(load(loader, c));
            }
            return out;
        }

        private static Class<?> load(final ClassLoader loader, final String name) {
            try {
                return loader.loadClass(name);
            } catch (final ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }

        private static List<String> getStringList(final Map<String, Object> t, final String key) {
            return ofNullable(t.get(key)).map(it -> (List<String>) it).orElse(null);
        }

        private static Optional<String> getString(final Map<String, Object> t, final String key) {
            return ofNullable(t.get(key)).map(String.class::cast);
        }
    }
}
