/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.hawt.boot.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.dependency.fromDependencies.AbstractDependencyFilterMojo;
import org.apache.maven.plugin.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugin.dependency.utils.filters.ResolveFileFilter;
import org.apache.maven.plugin.dependency.utils.markers.SourcesFileMarkerHandler;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;

/**
 * Builds a hawt boot assembly.
 *
 */
@Mojo( name = "build", requiresDependencyResolution = ResolutionScope.RUNTIME,
       defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true )
public class BuildMojo extends AbstractDependencyFilterMojo {

    /**
     * Directory used to create the assembly.
     *
     * @since 2.0
     */
    @Parameter( property = "hawt-boot.assembly",
                defaultValue = "${project.build.directory}/hawt-boot" )
    protected File assembly;

    /**
     * The main class to execute for the assembly.
     */
    @Parameter(property = "hawt-boot.main")
    protected String main;

    protected void doExecute() throws MojoExecutionException {

        // get sets of dependencies
        DependencyStatusSets results = this.getDependencySets(false, false);

        File libDir = new File(assembly, "lib");
        libDir.mkdirs();

        File binDir = new File(assembly, "bin");
        binDir.mkdirs();

        ArrayList<String> classpath = new ArrayList<String>();

        // Lets first copy this project's artifact.
        if( project.getArtifact().getFile()!=null ) {
            File target = new File(libDir, project.getArtifact().getFile().getName());
            classpath.add(target.getName());
            try {
                FileUtils.copyFile(project.getArtifact().getFile(), target);
            } catch (IOException e) {
                throw new MojoExecutionException("Could not copy artifact to lib dir", e);
            }
        }

        // Lets then copy the it's dependencies.
        for (Artifact artifact : results.getResolvedDependencies()) {
            File file = artifact.getFile().getAbsoluteFile();
            try {

                File target = new File(libDir, file.getName());

                // just in case we run into an lib name collision, lets
                // find a non-colliding target name
                int dupCounter = 1;
                while( classpath.contains(target.getName()) ) {
                    target = new File(libDir, "dup"+dupCounter+"-"+file.getName());
                    dupCounter++;
                }

                classpath.add(target.getName());
                FileUtils.copyFile( artifact.getFile(), target);

            } catch (IOException e) {
                throw new MojoExecutionException("Could not copy artifact to lib dir", e);
            }
        }

        // Finally lets write the classpath.
        String classpathTxt = StringUtils.join(classpath.iterator(), "\r\n")+"\r\n";
        try {
            FileUtils.fileWrite(new File(libDir, ".classpath"), classpathTxt);
        } catch (IOException e) {
            throw new MojoExecutionException("Could create the .classpath file", e);
        }

        HashMap<String, String> interpolations = new HashMap<String, String>();
        interpolations.put("mvn.artifactId", project.getArtifactId());
        if( main!=null ) {
            interpolations.put("mvn.main", main);
        }

        copyResource("bin/run", new File(binDir, "run"), "\n", interpolations);
        chmodExecutable(new File(binDir, "run"));

    }

    private void chmodExecutable(File file) {
        try {
            Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (Throwable ignore) {
            // we tried our best, perhaps the OS does not support posix file perms.
        }
    }

    private void copyResource(String source, File target, String separator, HashMap<String, String> interpolations) throws MojoExecutionException {

        try {
            String content = loadTextResource(getClass().getResource(source));
            if( interpolations!=null ) {
                content = StringUtils.interpolate(content, interpolations);
            }
            content = content.replaceAll("\\r?\\n", Matcher.quoteReplacement(separator));
            FileUtils.fileWrite(target, content);
        } catch (IOException e) {
            throw new MojoExecutionException("Could create the "+target+" file", e);
        }
    }

    private String loadTextResource(URL resource) throws IOException {
        InputStream is = resource.openStream();
        try {
            return IOUtil.toString(is, "UTF-8");
        } finally {
            IOUtil.close(is);
        }
    }

    protected ArtifactsFilter getMarkedArtifactFilter() {
        return new ResolveFileFilter(new SourcesFileMarkerHandler(this.markersDirectory));
    }
}
