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
package org.apache.geronimo.arthur.maven.mojo;

import static java.util.Collections.emptyMap;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.geronimo.arthur.impl.nativeimage.archive.Extractor;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstaller;
import org.apache.geronimo.arthur.impl.nativeimage.installer.SdkmanGraalVMInstallerConfiguration;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallResult;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

public abstract class ArthurMojo extends AbstractMojo {
    /**
     * native-image binary to use, if not set it will install graal in the local repository.
     */
    @Parameter(property = "arthur.nativeImage")
    protected String nativeImage;

    /**
     * Once built, the binary path is set in maven properties.
     * This enables to configure the prefix to use.
     */
    @Parameter(defaultValue = "arthur.", property = "arthur.propertiesPrefix")
    protected String propertiesPrefix;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${settings.offline}", readonly = true)
    protected boolean offline;

    //
    // Installer parameters
    //

    /**
     * In case Graal must be downloaded to get native-image, where to take it from.
     */
    @Parameter(property = "arthur.graalDownloadUrl",
            defaultValue = "https://api.sdkman.io/2/broker/download/java/${graalVersion}-grl/${platform}")
    private String graalDownloadUrl;

    /**
     * In case Graal must be downloaded to get native-image, which version to download.
     */
    @Parameter(property = "arthur.graalVersion", defaultValue = "19.2.1")
    private String graalVersion;

    /**
     * In case Graal must be downloaded to get native-image, which platform to download, auto will handle it for you.
     */
    @Parameter(property = "arthur.graalPlatform", defaultValue = "auto")
    private String graalPlatform;

    /**
     * In case Graal must be downloaded to get native-image, it will be cached in the local repository with this gav.
     */
    @Parameter(property = "arthur.graalCacheGav", defaultValue = "org.apache.geronimo.arthur.cache:graal")
    private String graalCacheGav; // groupId:artifactId

    /**
     * Where the temporary files are created.
     */
    @Parameter(defaultValue = "${project.build.directory}/arthur_workdir")
    protected File workdir;

    @Parameter(defaultValue = "${repositorySystemSession}")
    protected RepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepositories;

    @Component
    private RepositorySystem repositorySystem;

    protected boolean isInheritIO() {
        return false;
    }

    protected SdkmanGraalVMInstaller createInstaller() {
        final String graalPlatform = buildPlatform();
        final Extractor extractor = new Extractor();
        return new SdkmanGraalVMInstaller(SdkmanGraalVMInstallerConfiguration.builder()
                .offline(offline)
                .inheritIO(isInheritIO())
                .url(buildDownloadUrl(graalPlatform))
                .version(graalVersion)
                .platform(graalPlatform)
                .gav(buildCacheGav(graalPlatform))
                .workdir(workdir.toPath())
                .resolver(gav -> resolve(toArtifact(gav)).getFile().toPath())
                .installer((gav, file) -> install(file.toFile(), toArtifact(gav)))
                .extractor(extractor::unpack)
                .build());
    }

    protected org.eclipse.aether.artifact.Artifact toArtifact(final String s) {
        return new DefaultArtifact(s);
    }

    protected Path install(final File file, final org.eclipse.aether.artifact.Artifact art) {
        final org.eclipse.aether.artifact.Artifact artifact = new DefaultArtifact(
                art.getGroupId(),
                art.getArtifactId(),
                art.getClassifier(),
                art.getExtension(),
                art.getVersion(),
                emptyMap(),
                file);
        try {
            final InstallResult result = repositorySystem.install(
                    repositorySystemSession,
                    new InstallRequest().addArtifact(artifact));
            if (result.getArtifacts().isEmpty()) {
                throw new IllegalStateException("Can't install " + art);
            }
            return resolve(art).getFile().toPath();
        } catch (final InstallationException e) {
            throw new IllegalStateException(e);
        }
    }

    protected org.eclipse.aether.artifact.Artifact resolve(final org.eclipse.aether.artifact.Artifact art) {
        final ArtifactRequest artifactRequest =
                new ArtifactRequest().setArtifact(art).setRepositories(remoteRepositories);
        try {
            final ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
            if (result.isMissing()) {
                throw new IllegalStateException("Can't find " + art);
            }
            return result.getArtifact();
        } catch (final ArtifactResolutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String buildCacheGav(final String graalPlatform) {
        if (!graalPlatform.toLowerCase(ROOT).startsWith("linux")) { // cygwin
            return graalCacheGav + ":zip:" + graalPlatform + ':' + graalVersion;
        }
        return graalCacheGav + ":tar.gz:" + graalPlatform + ':' + graalVersion;
    }

    private String buildDownloadUrl(final String graalPlatform) {
        return graalDownloadUrl
                .replace("${graalVersion}", graalVersion)
                .replace("${platform}", graalPlatform);
    }

    private String buildPlatform() {
        if (!"auto".equals(graalPlatform)) {
            return graalPlatform;
        }
        return (System.getProperty("os.name", "linux") +
                ofNullable(System.getProperty("sun.arch.data.model"))
                        .orElseGet(() -> System.getProperty("os.arch", "64").replace("amd", "")))
                .toLowerCase(ROOT);
    }
}
