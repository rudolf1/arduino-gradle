package com.github.jlewallen.arduino;

import org.gradle.api.*
import org.gradle.api.plugins.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.incremental.*
import org.gradle.internal.service.ServiceRegistry
import org.gradle.api.internal.file.FileOperations
import org.gradle.platform.base.*
import org.gradle.model.*
import org.gradle.platform.base.component.*
import org.gradle.api.internal.project.ProjectIdentifier
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

class UploadTask extends DefaultTask {
    String port

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        if (port == null) {
            throw new GradleException("Please provide a Serial Port")
        }

        def String specifiedPort = port
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
}

@Managed
interface ArduinoComponentSpec extends GeneralComponentSpec {
    void setBoards(List<String> boards)
    List<String> getBoards()

    void setLibraries(List<String> libraries)
    List<String> getLibraries()
}

@Managed
interface ArduinoBinarySpec extends BinarySpec {
    void setProjectName(String name)
    String getProjectName()

    void setLibraries(List<String> libraries)
    List<String> getLibraries()

    void setBoard(String board)
    String getBoard()
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

        project.task("showPorts") << {
            def ports = SerialPort.getCommPorts()
            ports.each {
                println it.descriptivePortName
            }
        }
    }

    private static String[] getPreferencesCommandLine(Project project, BuildConfiguration config) {
        List<String> parts = []
        parts.addAll(["${project.arduino.home}/arduino-builder",
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
                      "${project.arduino.home}/libraries"])

        if (project.arduino.projectLibrariesDir) {
            parts.add("-libraries")
            parts.add("${project.arduino.projectLibrariesDir}")
        }

        parts.addAll(["-fqbn=${config.board}",
                      "-ide-version=10609",
                      "-build-path",
                      "${config.buildDir}",
                      "-warnings=none",
                      "-quiet"])
        return parts.toArray()
    }

    public static BuildConfiguration createBuildConfiguration(Project project, String arduinoHome, List<String> libraryNames,
                                                              String projectName, String projectLibrariesDir, List<String> preprocessorDefines,
                                                              String board) {
        def config = new BuildConfiguration()

        config.libraryNames = libraryNames
        config.projectName = projectName
        config.arduinoHome = arduinoHome
        config.projectDir = project.projectDir
        config.originalBuildDir = project.buildDir
        config.projectLibrariesDir = projectLibrariesDir ? new File((String)projectLibrariesDir) : null
        config.fileOperations = project
        config.board = board

        config.buildDir.mkdirs()

        def preferencesCommand = getPreferencesCommandLine(project, config)
        def String data = RunCommand.run(preferencesCommand, project.projectDir)

        def friendlyName = config.pathFriendlyBoardName
        def localCopy = new File(project.buildDir, "arduino.${friendlyName}.prefs")
        localCopy.write(data)

        def preferences = new Properties()
        preferences.load(new StringReader(data.replace("\\", "\\\\")))

        def extraFlags = preprocessorDefines.collect { "-D" + it }.join(" ")
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

    static class Rules extends RuleSource {
        @ComponentType
        public static void registerArduinoComponent(TypeBuilder<ArduinoComponentSpec> builder) {
        }

        @ComponentType
        public static void registerArduinoBinary(TypeBuilder<ArduinoBinarySpec> builder) {
        }

        @ComponentBinaries
        public static void generateBinaries(ModelMap<ArduinoBinarySpec> binaries, ServiceRegistry serviceRegistry,
                                            ProjectIdentifier projectId, ArduinoComponentSpec component) {
            // def fileOperations = serviceRegistry.get(FileOperations.class)
            component.boards.each { board ->
                binaries.create("exploded") { binary ->
                    binary.board = board
                    binary.libraries = component.libraries
                    binary.projectName = component.name
                }
            }
        }

        @BinaryTasks
        public static void createTasks(ModelMap<Task> tasks, ProjectIdentifier projectId, ArduinoBinarySpec binary) {
            def taskNameFriendlyBoardName = "-" + binary.board.replace(":", "-")
            def builder = ArduinoPlugin.createBuildConfiguration((Project)projectId, projectId.arduinoHome, binary.libraries, binary.projectName, null, [], binary.board)

            def compileTaskName = binary.tasks.taskName("compile", taskNameFriendlyBoardName)
            def archiveTaskName = binary.tasks.taskName("archive", taskNameFriendlyBoardName)
            def linkTaskName = binary.tasks.taskName("link", taskNameFriendlyBoardName)
            def uploadTaskName = binary.tasks.taskName("upload", taskNameFriendlyBoardName)

            tasks.create(compileTaskName, CompileTask.class, { task ->
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.sourceFiles = builder.allSourceFiles
                task.sourceFiles.each { task.inputs.file(it) }
                task.objectFiles.each { task.outputs.file(it) }
            })

            tasks.create(archiveTaskName, ArchiveTask.class, { task ->
                task.dependsOn(compileTaskName);
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.archiveFile = builder.archiveFile
                task.objectFiles.each { task.inputs.file(it) }
                task.outputs.file(task.archiveFile)
            })

            tasks.create(linkTaskName, LinkTask.class, { task ->
                task.dependsOn(archiveTaskName);
                task.buildConfiguration = builder
                task.objectFiles = builder.allObjectFiles
                task.archiveFile = builder.archiveFile
                task.binary = builder.binaryFile
            })

            if (false) {
                tasks.create(uploadTaskName, UploadTask.class, { task ->
                    task.dependsOn(linkTaskName);
                })
            }
        }
    }
}
