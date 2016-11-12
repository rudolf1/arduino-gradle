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

        def logFile = new File("log.txt")
        if (logFile.isFile()) {
            logFile.delete()
        }

        def puttyPath = findPuttyPath()
        def commandLine = "${puttyPath} -serial ${port} -sercfg 115200 -sessionlog log.txt"
        def parsed = CommandLine.translateCommandLine(commandLine)
        println(commandLine)

        def processBuilder = new ProcessBuilder(parsed)
        processBuilder.redirectErrorStream(true)
        processBuilder.directory(project.projectDir)
        def process = processBuilder.start()
    }

    def findPuttyPath() {
        def puttyPaths = [
            "./",
            "C:/Windows/System32"
        ]

        def files = puttyPaths.collect { new File(it, "putty.exe") }
        def found = files.findAll {
            return it.isFile()
        }
        if (found.size() == 0) {
            println files
            throw new Exception("Unable to find putty.exe")
        }

        return found[0]
    }
}
