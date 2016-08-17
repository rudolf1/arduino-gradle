import org.gradle.api.*
import org.gradle.api.plugins.*
import java.lang.ProcessBuilder

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

        project.task('upload', dependsOn: 'build') << {
            def uploadCommand = lastBuild.getUploadCommand("COM10")
            logger.debug(uploadCommand)
            println(uploadCommand)

            def processBuilder = new ProcessBuilder(uploadCommand)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            processBuilder.directory(project.projectDir)
              
            def p = processBuilder.start()
            // RunCommand.run(uploadCommand, project.projectDir, true)
            // def p = uploadCommand.execute()
            p.waitFor()
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
