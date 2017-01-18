package com.github.jlewallen.arduino

import groovy.util.logging.Slf4j
import org.conservify.firmwaretool.uploading.DevicePorts
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*
import org.conservify.firmwaretool.uploading.Uploader

@Slf4j
class UploadTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    String port
    DevicePorts ports

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def config = buildConfiguration.uploadConfig
        def uploader = new Uploader(new GradlePortDiscoveryInteraction())
        def ports = uploader.upload(buildConfiguration.binaryFile, config)
        this.ports = ports
    }
}


