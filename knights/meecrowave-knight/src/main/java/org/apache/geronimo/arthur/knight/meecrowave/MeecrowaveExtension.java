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
package org.apache.geronimo.arthur.knight.meecrowave;

import lombok.extern.slf4j.Slf4j;
import org.apache.geronimo.arthur.spi.ArthurExtension;
import org.apache.geronimo.arthur.spi.model.ClassReflectionModel;
import org.apache.geronimo.arthur.spi.model.ResourceBundleModel;
import org.apache.geronimo.arthur.spi.model.ResourceModel;

import java.util.Collections;
import java.util.stream.Stream;

@Slf4j
public class MeecrowaveExtension implements ArthurExtension {
    @Override
    public void execute(final Context context) {
        registerReflections(context);
        registerResources(context);
        registerIncludeResourceBundles(context);

        context.setProperty("annotation.custom.annotations.properties",
                "javax.json.bind.annotation.JsonbProperty:allDeclaredConstructors=true|allDeclaredMethods=true|allDeclaredFields=true," +
                "org.apache.meecrowave.runner.cli.CliOption:allDeclaredFields=true");

        context.setProperty("openwebbeans.extension.excludes",
                "org.apache.cxf.Bus,org.apache.cxf.common.util.ClassUnwrapper," +
                "org.apache.cxf.interceptor.InterceptorProvider," +
                "io.yupiik.logging.jul," +
                "org.apache.openwebbeans.se");

        context.addNativeImageOption("-Dopenwebbeans.logging.factory=org.apache.webbeans.logger.JULLoggerFactory");
        context.addNativeImageOption("-Djava.util.logging.manager=io.yupiik.logging.jul.YupiikLogManager");
    }

    private void registerReflections(final Context context) {
        Stream.of("org.apache.cxf.BusFactory").map(it -> {
            ClassReflectionModel crm = new ClassReflectionModel();
            crm.setName(it);
            return crm;
        }).forEach(context::register);

        Stream.of(
                "javax.ws.rs.core.UriInfo",
                "javax.ws.rs.core.HttpHeaders",
                "javax.ws.rs.core.Request",
                "javax.ws.rs.core.SecurityContext",
                "javax.ws.rs.ext.Providers",
                "javax.ws.rs.ext.ContextResolver",
                "javax.servlet.http.HttpServletRequest",
                "javax.servlet.http.HttpServletResponse",
                "javax.ws.rs.core.Application")
                .map(it -> {
                    ClassReflectionModel crm = new ClassReflectionModel();
                    crm.setName(it);
                    crm.setAllPublicMethods(true);
                    return crm;
                })
                .forEach(context::register);

        Stream.of(
                "org.apache.meecrowave.cxf.JAXRSFieldInjectionInterceptor",
                "org.apache.cxf.cdi.DefaultApplication")
                .map(it -> {
                    ClassReflectionModel crm = new ClassReflectionModel();
                    crm.setName(it);
                    crm.setAllPublicMethods(true);
                    crm.setAllPublicConstructors(true);
                    return crm;
                })
                .forEach(context::register);

        Stream.of(
                "org.apache.cxf.bus.managers.CXFBusLifeCycleManager",
                "org.apache.cxf.bus.managers.ClientLifeCycleManagerImpl",
                "org.apache.cxf.bus.managers.EndpointResolverRegistryImpl",
                "org.apache.cxf.bus.managers.HeaderManagerImpl",
                "org.apache.cxf.bus.managers.PhaseManagerImpl",
                "org.apache.cxf.bus.managers.ServerLifeCycleManagerImpl",
                "org.apache.cxf.bus.managers.ServerRegistryImpl",
                "org.apache.cxf.bus.managers.WorkQueueManagerImpl",
                "org.apache.cxf.bus.resource.ResourceManagerImpl",
                "org.apache.cxf.catalog.OASISCatalogManager",
                "org.apache.cxf.common.spi.ClassLoaderProxyService",
                "org.apache.cxf.common.util.ASMHelperImpl",
                "org.apache.cxf.service.factory.FactoryBeanListenerManager",
                "org.apache.cxf.transport.http.HTTPTransportFactory",
                "org.apache.cxf.catalog.OASISCatalogManager",
                "org.apache.cxf.endpoint.ClientLifeCycleManager",
                "org.apache.cxf.buslifecycle.BusLifeCycleManager",
                "org.apache.cxf.phase.PhaseManager",
                "org.apache.cxf.resource.ResourceManager",
                "org.apache.cxf.headers.HeaderManager",
                "org.apache.cxf.common.util.ASMHelper",
                "org.apache.cxf.common.spi.ClassLoaderService",
                "org.apache.cxf.endpoint.EndpointResolverRegistry",
                "org.apache.cxf.endpoint.ServerLifeCycleManager",
                "org.apache.cxf.workqueue.WorkQueueManager",
                "org.apache.cxf.endpoint.ServerRegistry",
                "org.apache.cxf.jaxrs.JAXRSBindingFactory",
                "org.apache.webbeans.web.lifecycle.WebContainerLifecycle",
                "org.apache.meecrowave.logging.tomcat.LogFacade",
                "org.apache.catalina.servlets.DefaultServlet",
                "org.apache.catalina.authenticator.NonLoginAuthenticator")
                .map(it -> {
                    ClassReflectionModel crm = new ClassReflectionModel();
                    crm.setName(it);
                    crm.setAllPublicConstructors(true);
                    return crm;
                })
                .forEach(context::register);

        Stream.of(
                "org.apache.cxf.transport.http.Headers",
                "org.apache.catalina.loader.WebappClassLoader",
                "org.apache.tomcat.util.descriptor.web.WebXml",
                "org.apache.coyote.http11.Http11NioProtocol",
                "javax.servlet.ServletContext")
                .map(it -> {
                    ClassReflectionModel crm = new ClassReflectionModel();
                    crm.setName(it);
                    crm.setAllPublicMethods(true);
                    return crm;
                })
                .forEach(context::register);

        ClassReflectionModel crm = new ClassReflectionModel();
        crm.setName("org.apache.cxf.jaxrs.provider.ProviderFactory");
        ClassReflectionModel.MethodReflectionModel method = new ClassReflectionModel.MethodReflectionModel();
        method.setName("getReadersWriters");
        crm.setMethods(Collections.singletonList(method));
        context.register(crm);

        crm = new ClassReflectionModel();
        crm.setName("org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider$ProvidedInstance");
        ClassReflectionModel.FieldReflectionModel field = new ClassReflectionModel.FieldReflectionModel();
        field.setName("instance");
        crm.setFields(Collections.singletonList(field));
        context.register(crm);

        crm = new ClassReflectionModel();
        crm.setName("org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider");
        field = new ClassReflectionModel.FieldReflectionModel();
        field.setName("providers");
        crm.setFields(Collections.singletonList(field));
        context.register(crm);

        crm = new ClassReflectionModel();
        crm.setName("org.apache.xbean.finder.AnnotationFinder");
        field = new ClassReflectionModel.FieldReflectionModel();
        field.setName("linking");
        field.setAllowWrite(true);
        crm.setFields(Collections.singletonList(field));
        context.register(crm);
    }

    private void registerResources(final Context context) {
        Stream.of(
                "org\\/apache\\/catalina\\/.*\\.properties",
                "javax\\/servlet\\/(jsp\\/)?resources\\/.*\\.(xsd|dtd)",
                "meecrowave\\.properties",
                "META-INF/cxf/bus-extensions\\.txt",
                "org/apache/cxf/version/version\\.properties")
                .map(it -> {
                    ResourceModel rm = new ResourceModel();
                    rm.setPattern(it);
                    return rm;
                })
                .forEach(context::register);
    }

    private void registerIncludeResourceBundles(final Context context) {
        Stream.of(
                "org.apache.cxf.Messages",
                "org.apache.cxf.interceptor.Messages",
                "org.apache.cxf.bus.managers.Messages",
                "org.apache.cxf.jaxrs.Messages",
                "org.apache.cxf.jaxrs.provider.Messages",
                "org.apache.cxf.jaxrs.interceptor.Messages",
                "org.apache.cxf.jaxrs.utils.Messages",
                "org.apache.cxf.transport.servlet.Messages",
                "org.apache.catalina.authenticator.LocalStrings",
                "org.apache.catalina.connector.LocalStrings",
                "org.apache.catalina.core.LocalStrings",
                "org.apache.catalina.deploy.LocalStrings",
                "org.apache.catalina.filters.LocalStrings",
                "org.apache.catalina.loader.LocalStrings",
                "org.apache.catalina.manager.host.LocalStrings",
                "org.apache.catalina.manager.LocalStrings",
                "org.apache.catalina.mapper.LocalStrings",
                "org.apache.catalina.realm.LocalStrings",
                "org.apache.catalina.security.LocalStrings",
                "org.apache.catalina.servlets.LocalStrings",
                "org.apache.catalina.session.LocalStrings",
                "org.apache.catalina.startup.LocalStrings",
                "org.apache.catalina.users.LocalStrings",
                "org.apache.catalina.util.LocalStrings",
                "org.apache.catalina.valves.LocalStrings",
                "org.apache.catalina.valves.rewrite.LocalStrings",
                "org.apache.catalina.webresources.LocalStrings",
                "org.apache.coyote.http11.filters.LocalStrings",
                "org.apache.coyote.http11.LocalStrings",
                "org.apache.coyote.http11.upgrade.LocalStrings",
                "org.apache.coyote.http2.LocalStrings",
                "org.apache.coyote.LocalStrings",
                "org.apache.tomcat.util.buf.LocalStrings",
                "org.apache.tomcat.util.codec.binary.LocalStrings",
                "org.apache.tomcat.util.compat.LocalStrings",
                "org.apache.tomcat.util.descriptor.LocalStrings",
                "org.apache.tomcat.util.descriptor.tld.LocalStrings",
                "org.apache.tomcat.util.descriptor.web.LocalStrings",
                "org.apache.tomcat.util.digester.LocalStrings",
                "org.apache.tomcat.util.http.LocalStrings",
                "org.apache.tomcat.util.http.parser.LocalStrings",
                "org.apache.tomcat.util.json.LocalStrings",
                "org.apache.tomcat.util.LocalStrings",
                "org.apache.tomcat.util.modeler.LocalStrings",
                "org.apache.tomcat.util.net.jsse.LocalStrings",
                "org.apache.tomcat.util.net.LocalStrings",
                "org.apache.tomcat.util.net.openssl.ciphers.LocalStrings",
                "org.apache.tomcat.util.net.openssl.LocalStrings",
                "org.apache.tomcat.util.scan.LocalStrings",
                "org.apache.tomcat.util.security.LocalStrings",
                "org.apache.tomcat.util.threads.res.LocalStrings",
                "javax.servlet.LocalStrings",
                "javax.servlet.http.LocalStrings")
                .map(it -> {
                    ResourceBundleModel rbm = new ResourceBundleModel();
                    rbm.setName(it);
                    return rbm;
                })
                .forEach(context::register);
    }

}
