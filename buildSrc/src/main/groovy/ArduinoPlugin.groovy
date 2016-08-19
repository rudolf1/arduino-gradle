import org.gradle.api.*
import org.gradle.api.plugins.*
import java.lang.ProcessBuilder
import com.fazecast.jSerialComm.*

class ArduinoPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("arduino", ArduinoPluginExtension)

        def BuildConfiguration lastBuild;

        project.task('build', dependsOn: []) << {
            def builder = lastBuild = createBuildConfiguration(project, project.arduino.defaultBoard)
            builder.build()
        }

        project.task('buildAll') << {
            project.arduino.boards.each {
                def builder = createBuildConfiguration(project, it)
                builder.build()
            }
        }

        project.task("showPorts") << {
            def ports = SerialPort.getCommPorts()
            ports.each {
                println it.descriptivePortName
            }
        }

        project.task("showPossiblePorts") << {
            def builder = createBuildConfiguration(project, project.arduino.defaultBoard)
            def ports = SerialPort.getCommPorts().findAll { it.descriptivePortName.contains builder.usbProduct }
            ports.each {
                println it.descriptivePortName
            }
        }

        project.task('upload', dependsOn: 'build') << {
            if (project.port == null) {
                throw new GradleException("Please provide a Serial Port")
            }

            def String specifiedPort = project.port
            def newPort = Uploader.discoverPort(specifiedPort, lastBuild.use1200BpsTouch)
            if (!newPort)  {
                throw new GradleException("No port") 
            }

            def uploadCommand = lastBuild.getUploadCommand(newPort)
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

        project.task('clean') << {
            ant.delete(dir: project.buildDir)
        }

    }

    private String[] getPreferencesCommandLine(Project project, BuildConfiguration config) {
        return ["${project.arduinoHome}/arduino-builder",
                 "-dump-prefs",
                 "-logger=machine",
                 "-hardware",
                 "${project.arduinoHome}/hardware",
                 "-hardware",
                 "${project.arduino.arduinoPackagesDir}/packages",
                 "-tools",
                 "${project.arduinoHome}/tools-builder",
                 "-tools",
                 "${project.arduinoHome}/hardware/tools/avr",
                 "-tools",
                 "${project.arduino.arduinoPackagesDir}/packages",
                 "-built-in-libraries",
                 "${project.arduinoHome}/libraries",
                 "-libraries",
                 "${project.arduino.projectLibrariesDir}",
                 "-fqbn=${config.board}",
                 "-ide-version=10609",
                 "-build-path",
                 "${config.buildDir}",
                 "-warnings=none",
                 "-quiet"]
    }

    private BuildConfiguration createBuildConfiguration(Project project, String board) {
        def config = new BuildConfiguration()

        config.libraryNames = project.arduino.libraries
        config.projectName = project.arduino.projectName
        config.arduinoHome = project.arduinoHome
        config.projectDir = project.projectDir
        config.provideMain = project.arduino.provideMain
        config.originalBuildDir = project.buildDir
        config.board = board

        config.buildDir.mkdirs()

        def preferencesCommand = getPreferencesCommandLine(project, config)
        def String data = RunCommand.run(preferencesCommand, project.projectDir)

        def friendlyName = config.pathFriendlyBoardName
        def localCopy = new File(project.buildDir, "arduino.${friendlyName}.prefs")
        localCopy.write(data)

        def preferences = new Properties()
        preferences.load(new StringReader(data.replace("\\", "\\\\")))

        config.preferences = preferences

        return config
    }

}
