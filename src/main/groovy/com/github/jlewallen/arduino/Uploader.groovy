package com.github.jlewallen.arduino;

import com.fazecast.jSerialComm.*

class Uploader {
    static String[] perform1200bpsTouch(String portName) {
        def serialPort = SerialPort.getCommPort(portName)
        serialPort.setBaudRate(1200)
        if (serialPort.openPort()) {
            serialPort.closePort()
            return lookForNewPort(portNames, 5)
        }

        return null
    }

    static boolean waitForPort(String portName, tries = 10) {
        while (tries-- > 0) {
            sleep(500)
            def portNames = getPortNames()
            if (portNames.contains(portName)) {
                return true
            }
        }

        return false
    }

    static String[] lookForNewPort(String[] portNamesBefore, tries = 10) {
        while (tries-- > 0) {
            sleep(500)
            def portNamesNow = portNames
            def missingPorts = (portNamesBefore - portNamesNow)
            def newPorts = (portNamesNow - portNamesBefore)

            print portNamesBefore
            print " -> "
            print portNamesNow
            print ": "
            print missingPorts
            print " / "
            println newPorts

            if (newPorts.size() > 0) {
                return [ newPorts[0], missingPorts[0] ]
            }
        }

        return null
    }

    static String[] discoverPort(String specifiedPort, boolean perform1200bpsTouch) {
        def newPort = specifiedPort
        def ports
        def serialPort = specifiedPort ? SerialPort.getCommPort(specifiedPort) : null
        if (perform1200bpsTouch && specifiedPort) {
            print "Given: "
            println specifiedPort
            ports = Uploader.perform1200bpsTouch(specifiedPort)
            if (ports == null) {
                return [ specifiedPort, specifiedPort ]
            }
        }

        if (ports == null) {
            println ""
            println "ERROR: Unable to find the specified port, try resetting while I look."
            println "ERROR: Press RESET and cross your fingers."
            println ""
            def found = lookForNewPort(portNames, 20)
            if (found) {
                return found
            }
            return null
        }

        return [ newPort ]
    }

    static String[] getPortNames() {
        def currentPorts = SerialPort.getCommPorts()
        return currentPorts.collect { it.systemPortName }
    }
}
