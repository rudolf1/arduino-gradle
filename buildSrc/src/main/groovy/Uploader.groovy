import com.fazecast.jSerialComm.*

class Uploader {
    static String perform1200bpsTouch(String portName) {
        def portNamesBefore = portNames
        def serialPort = SerialPort.getCommPort(portName)
        serialPort.setBaudRate(1200)
        if (serialPort.openPort()) {
            serialPort.closePort()
            return lookForNewPort(5)
        }

        return null
    }

    static String lookForNewPort(String[] portNamesBefore, tries = 10) {
        while (tries-- > 0) {
            sleep(500)
            def portNamesNow = portNames
            def newPorts = (portNamesNow - portNamesBefore)

            print portNamesBefore
            print " -> "
            print portNamesNow
            print ": "
            println newPorts

            if (newPorts.size() > 0) {
                return newPorts[0]
            }
        }

        return null
    }

    static String discoverPort(String specifiedPort, boolean perform1200bpsTouch) {
        def newPort = specifiedPort
        def serialPort = SerialPort.getCommPort(specifiedPort)
        if (perform1200bpsTouch) {
            newPort = Uploader.perform1200bpsTouch(specifiedPort)
        }

        if (newPort == null) {
            println ""
            println "ERROR: Unable to find the specified port, try resetting while I look."
            println "ERROR: Press RESET and cross your fingers."
            println ""
            return lookForNewPort(portNames, 20)
        }

        return null
    }

    static String[] getPortNames() {
        def currentPorts = SerialPort.getCommPorts()
        return currentPorts.collect { it.systemPortName }
    }
}
