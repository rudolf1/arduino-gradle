package com.github.jlewallen.arduino;

import org.gradle.model.Managed

@Managed
interface ArduinoInstallation {
    void setHome(String packagesDir)
    String getHome()

    void setPackagesDir(String packagesDir)
    String getPackagesDir()
}
