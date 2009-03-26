/*
 * Copyright (C) 2007-2008 JVending Masa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jvending.masa.plugin.platformtest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.jvending.masa.CommandExecutor;
import org.jvending.masa.ExecutionException;
import org.jvending.masa.plugin.AbstractAndroidMojo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Installs relevant apk files to device, and runs the tests on device. Apk files that are installed to the device are:
 * <ul>
 * <li>the platformtest apk itself,</li>
 * <li>any dependencies of &lt;type&gt;android:apk&lt;/type&gt; in the platformtest pom.</li>
 * </ul>
 *
 * @goal platformtestTest
 * @phase integration-test
 */
public class PlatformTesterMojo extends AbstractAndroidMojo {

    /**
     * Package name of the apk we wish to test.
     * @optional
     * @parameter expression="${masa.test.targetPackage}
     */
    private String testsPackage;

    /**
     * Class name of test runner.
     * @optional
     * @parameter default-value="android.test.InstrumentationTestRunner" expression=${masa.test.testRunner}
     */
    private String testRunner;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(testsPackage == null) {
            testsPackage = extractPackageNameFromAndroidManifest(androidManifestFile);
        }

        // Install any target apk's to device
        Set<Artifact> directDependentArtifacts = project.getDependencyArtifacts();
        if (directDependentArtifacts != null) {
            for (Artifact artifact : directDependentArtifacts) {
                String type = artifact.getType();
                if (type.equals("android:apk")) {
                    getLog().debug("Detected android:apk dependency " + artifact + ". Will resolve and install to device...");
                    final File targetApkFile = resolveArtifactToFile(artifact);
                    if (isUninstallApkBeforeInstallingToDevice()){
                        getLog().debug("Attempting uninstall of " + targetApkFile + " from device...");
                        uninstallApkFromDevice(targetApkFile);
                    }
                    getLog().debug("Installing " + targetApkFile + " to device...");
                    installApkToDevice(targetApkFile);
                }
            }
        }


        CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(this.getLog());

        List<String> commands = new ArrayList<String>();
        commands.add("shell");
        commands.add("am");
        commands.add("instrument");
        commands.add( "-w");
        commands.add( testsPackage + "/" + testRunner);
        
        getLog().info("adb " + commands.toString());
        try {
            executor.executeCommand("adb", commands, project.getBasedir(), true);
            final String standardOut   = executor.getStandardOut  ();
            final String standardError = executor.getStandardError();
            getLog().debug(standardOut);
            getLog().debug(standardError);
            // Fail when tests on device fail. adb does not exit with errorcode!=0 or even print to stderr, so we have to parse stdout.
            if (standardOut == null || !standardOut.matches(".*?OK \\([0-9]+ tests?\\)\\s*")){
                throw new MojoFailureException("Tests failed on device.");
            }
        } catch (ExecutionException e) {
            getLog().error(executor.getStandardOut());
            getLog().error(executor.getStandardError());
            throw new MojoFailureException("Tests failed on device.");
        }
    }

}