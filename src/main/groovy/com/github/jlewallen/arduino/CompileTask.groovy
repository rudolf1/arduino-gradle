package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class CompileTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] sourceFiles
    File[] objectFiles

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        inputs.outOfDate { change ->
            buildConfiguration.buildFile(change.file)
        }

        inputs.removed { change ->
            buildConfiguration.buildFile(change.file)
        }
    }
}
