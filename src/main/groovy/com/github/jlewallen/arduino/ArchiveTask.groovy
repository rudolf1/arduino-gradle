package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class ArchiveTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] objectFiles
    File archiveFile

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        buildConfiguration.linkArchive(objectFiles, archiveFile)
    }
}
