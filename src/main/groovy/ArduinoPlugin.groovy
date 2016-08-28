package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*
import java.lang.ProcessBuilder
import com.fazecast.jSerialComm.*
import groovy.util.logging.Slf4j

class CompileTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] sourceFiles
    File[] objectFiles

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        inputs.outOfDate { change ->
            buildConfiguration.buildFile(change.file)
        }

        inputs.removed { change ->
            buildConfiguration.buildFile(change.file)
        }
    }
}

class ArchiveTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] objectFiles
    File archiveFile

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        buildConfiguration.linkArchive(objectFiles, archiveFile)
    }
}

class LinkTask extends DefaultTask {
    BuildConfiguration buildConfiguration
    File[] objectFiles
    File archiveFile
    File binary

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        buildConfiguration.linkBinary(objectFiles, archiveFile)
    }
}

@Slf4j
class ArduinoPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("arduino", ArduinoPluginExtension)

        project.arduino.home = guessArduinoHomeIfNecessary(project)
        if (project.arduino.home == null) {
            throw new GradleException("No ArduinoHome!");
        }
        project.arduino.arduinoPackagesDir = guessArduinoPackagesDirectoryIfNecessary(project)

        if (!project.arduino.home) {
            log.error("No arduinoHome configured or available!")
            throw new GradleException("No arduinoHome configured or available!")
        }

        project.afterEvaluate {
            def builder = createBuildConfiguration(project, project.arduino.defaultBoard)

            project.tasks.create('compile', CompileTask.class, { task ->
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.sourceFiles = builder.allSourceFiles
                task.sourceFiles.each { task.inputs.file(it) }
                task.objectFiles.each { task.outputs.file(it) }
            })

            project.tasks.create('archive', ArchiveTask.class, { task ->
                task.dependsOn('compile');
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.archiveFile = builder.archiveFile
                task.objectFiles.each { task.inputs.file(it) }
                task.outputs.file(task.archiveFile)
            })

            project.tasks.create('link', LinkTask.class, { task ->
                task.dependsOn('archive');
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.archiveFile = builder.archiveFile
                task.binary = builder.binaryFile
            })
        }

        def BuildConfiguration lastBuild;

        project.task('build', dependsOn: ['link']) << {
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
        return ["${project.arduino.home}/arduino-builder",
                 "-dump-prefs",
                 "-logger=machine",
                 "-hardware",
                 "${project.arduino.home}/hardware",
                 "-hardware",
                 "${project.arduino.arduinoPackagesDir}/packages",
                 "-tools",
                 "${project.arduino.home}/tools-builder",
                 "-tools",
                 "${project.arduino.home}/hardware/tools/avr",
                 "-tools",
                 "${project.arduino.arduinoPackagesDir}/packages",
                 "-built-in-libraries",
                 "${project.arduino.home}/libraries",
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
        config.arduinoHome = project.arduino.home
        config.projectDir = project.projectDir
        config.provideMain = project.arduino.provideMain
        config.originalBuildDir = project.buildDir
        config.projectLibrariesDir = project.arduino.projectLibrariesDir ? new File((String)project.arduino.projectLibrariesDir) : null
        config.project = project
        config.board = board

        config.buildDir.mkdirs()

        def preferencesCommand = getPreferencesCommandLine(project, config)
        def String data = RunCommand.run(preferencesCommand, project.projectDir)

        def friendlyName = config.pathFriendlyBoardName
        def localCopy = new File(project.buildDir, "arduino.${friendlyName}.prefs")
        localCopy.write(data)

        def preferences = new Properties()
        preferences.load(new StringReader(data.replace("\\", "\\\\")))

        def extraFlags = project.arduino.preprocessorDefines.collect { "-D" + it }.join(" ")
        preferences["compiler.c.extra_flags"] += " " + extraFlags
        preferences["compiler.cpp.extra_flags"] += " " + extraFlags

        config.preferences = preferences

        return config
    }

    private String[] arduinoHomeSearchPaths() {
        return [
            "../arduino-*/arduino-builder*",
            "arduino-*/arduino-builder*"
        ]
    }

    private String guessArduinoPackagesDirectoryIfNecessary(Project project) {
        if (project.arduino.arduinoPackagesDir) {
            return project.arduino.arduinoPackagesDir
        }

        return new File(new File((String)project.arduino.home).parent, "arduino-packages")
    }

    private String guessArduinoHomeIfNecessary(Project project) {
        if (project.hasProperty("arduinoHome") && project.arduinoHome) {
            File file = new File(project.arduinoHome)
            if (file.isAbsolute()) {
                return file
            }
            return new File(project.rootProject.projectDir, project.arduinoHome)
        }

        def candidates = arduinoHomeSearchPaths().collect {
            def tree = project.fileTree(project.rootProject.projectDir)
            return tree.include(it)
        }.collect { it.files }.flatten().unique().sort()

        if (candidates.size() == 1) {
            return candidates[0].parent
        }
        else if (candidates.size() > 1) {
            println candidates
        }

        return null
    }

}
