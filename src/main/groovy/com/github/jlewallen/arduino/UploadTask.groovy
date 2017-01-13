package com.github.jlewallen.arduino

import groovy.util.logging.Slf4j
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*
import org.conservify.firmwaretool.uploading.Uploader
import org.conservify.firmwaretool.uploading.PortDiscoveryInteraction

@Slf4j
class UploadTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    String port
    String[] ports

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def config = buildConfiguration.uploadConfig
        def uploader = new Uploader(new GradlePortDiscoveryInteraction())
        def ports = uploader.upload(buildConfiguration.binaryFile, config)

        if (ports) {
            this.ports = [ ports.uploadPort, ports.touchPort ]
        }
    }
}

class GradlePortDiscoveryInteraction implements PortDiscoveryInteraction {
    @Override
    void onBeginning() {
        println "---------------------------------------------------------------------"
        println "ERROR: Unable to find the specified port, try resetting while I look."
        println "ERROR: Press RESET and cross your fingers."
        println "---------------------------------------------------------------------"
    }

    @Override
    void onPortStatus(String[] portNamesBefore, String[] portNamesNow, String[] missingPorts, String[] newPorts) {
        print portNamesBefore
        print " -> "
        print portNamesNow
        print ": "
        print missingPorts
        print " / "
        print newPorts
        println ""
    }

    @Override
    void onProgress(String line) {
        println line
    }
}

