package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class UploadTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    String port
    String[] ports

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def String specifiedPort = port
        def ports = Uploader.discoverPort(specifiedPort, buildConfiguration.use1200BpsTouch)
        if (!ports)  {
            throw new GradleException("No port")
        }

        def uploadCommand = buildConfiguration.getUploadCommand(ports[0])
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

        this.ports = ports
    }
}
