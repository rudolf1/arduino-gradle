import com.fazecast.jSerialComm.*

class Uploader {
    def static String perform1200bpsTouch(String portName) {
        def portsBefore = SerialPort.getCommPorts()
        def portNamesBefore = portsBefore.collect { it.systemPortName }
        def serialPort = SerialPort.getCommPort(portName)
        serialPort.setBaudRate(1200)
        if (serialPort.openPort()) {
            serialPort.closePort()

            def tries = 10
            while (tries-- > 0) {
                sleep(250)
                def portsNow = SerialPort.getCommPorts()
                def portNamesNow = portsNow.collect { it.systemPortName }
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
        }

        return null
    }
}
