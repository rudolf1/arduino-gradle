package com.github.jlewallen.arduino

import org.conservify.firmwaretool.monitoring.SerialMonitor
import org.conservify.firmwaretool.uploading.DevicePorts
import org.conservify.firmwaretool.util.SettingsCache;
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*

class MonitorTask extends DefaultTask {
    UploadTask uploadTask
    String givenPort

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        def logFile = new File("log.txt")
        if (logFile.isFile()) {
            logFile.delete()
        }

        DevicePorts ports = uploadTask.ports
        if (ports == null && givenPort != null) {
            ports = new DevicePorts(givenPort, givenPort, false)
        }

        if (ports == null) {
            def settings = SettingsCache.get()
            if (settings.lastTouchPort != null) {
                ports = new DevicePorts(settings.lastUploadPort, settings.lastTouchPort, false)
            }
            else {
                println "No known port for SerialMonitor, try specifying -Pport="
                return
            }
        }

        SerialMonitor monitor = new SerialMonitor()
        monitor.open(ports, 115200, logFile)
    }
}
