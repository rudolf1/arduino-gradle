package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class MonitorTask extends DefaultTask {
    UploadTask uploadTask

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def port = uploadTask.ports[-1]

        Uploader.waitForPort(port)

        def commandLine = "putty.exe -serial ${port} -sercfg 115200 -sessionlog log.txt"
        def parsed = CommandLine.translateCommandLine(commandLine)
        println(commandLine)

        def processBuilder = new ProcessBuilder(parsed)
        processBuilder.redirectErrorStream(true)
        processBuilder.directory(project.projectDir)
        def process = processBuilder.start()
    }
}
