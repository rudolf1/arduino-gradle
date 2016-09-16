package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class UploadTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    String port

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        if (port == null) {
            throw new GradleException("Please provide a Serial Port")
        }

        def String specifiedPort = port
        def newPort = Uploader.discoverPort(specifiedPort, buildConfiguration.use1200BpsTouch)
        if (!newPort)  {
            throw new GradleException("No port")
        }

        def uploadCommand = buildConfiguration.getUploadCommand(newPort)
        def parsed = CommandLine.translateCommandLine(uploadCommand)
        logger.debug(uploadCommand)
        println(uploadCommand)

        def processBuilder = new ProcessBuilder(parsed)
        processBuilder.redirectErrorStream(true)
        processBuilder.directory(project.projectDir)
        def process = processBuilder.start()
        def InputStream so = process.getInputStream()
        def BufferedReader reader = new BufferedReader(new InputStreamReader(so))
        def line
        while ((line = reader.readLine()) != null) {
            System.out.println(line)
        }
        process.waitFor()
    }
}
