package com.github.jlewallen.arduino

import org.conservify.firmwaretool.monitoring.SerialMonitor;
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class MonitorTask extends DefaultTask {
    UploadTask uploadTask

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def ports = uploadTask.ports

        def logFile = new File("log.txt")
        if (logFile.isFile()) {
            logFile.delete()
        }

        SerialMonitor monitor = new SerialMonitor()
        monitor.open(ports, 115200, logFile)
    }
}
