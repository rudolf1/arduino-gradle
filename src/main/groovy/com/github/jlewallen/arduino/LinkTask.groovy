package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class LinkTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] objectFiles
    File archiveFile
    File binary

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        buildConfiguration.linkBinary(objectFiles, archiveFile)
    }
}
