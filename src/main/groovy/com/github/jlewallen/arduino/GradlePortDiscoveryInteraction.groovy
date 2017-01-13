package com.github.jlewallen.arduino

import org.conservify.firmwaretool.uploading.PortDiscoveryInteraction

class GradlePortDiscoveryInteraction implements PortDiscoveryInteraction {
    @Override
    void onBeginning() {
        println "---------------------------------------------------------------------"
        println "ERROR: Unable to find the specified port, try resetting while I look."
        println "ERROR: Press RESET and cross your fingers."
        println "---------------------------------------------------------------------"
    }

    @Override
    void onPortStatus(String[] portNamesBefore, String[] portNamesNow, String[] missingPorts, String[] newPorts) {
        print portNamesBefore
        print " -> "
        print portNamesNow
        print ": "
        print missingPorts
        print " / "
        print newPorts
        println ""
    }

    @Override
    void onProgress(String line) {
        println line
    }
}
