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

@Slf4j
class ArduinoPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.task("showPorts") << {
            def ports = SerialPort.getCommPorts()
            ports.each {
                println it.descriptivePortName
            }
        }
    }

    private static String[] getPreferencesCommandLine(ArduinoInstallation installation, BuildConfiguration config) {
        List<String> parts = ["${installation.home}/arduino-builder",
                              "-dump-prefs",
                              "-logger=machine",
                              "-hardware",
                              "${installation.home}/hardware",
                              "-hardware",
                              "${installation.packagesDir}/packages",
                              "-tools",
                              "${installation.home}/tools-builder",
                              "-tools",
                              "${installation.home}/hardware/tools/avr",
                              "-tools",
                              "${installation.packagesDir}/packages",
                              "-built-in-libraries",
                              "${installation.home}/libraries"]

        if (config.projectLibrariesDir) {
            parts.add("-libraries")
            parts.add("${config.projectLibrariesDir}")
        }

        parts.addAll(["-fqbn=${config.board}",
                      "-ide-version=10609",
                      "-build-path",
                      "${config.buildDir}",
                      "-warnings=none",
                      "-quiet"])
        return parts.toArray()
    }

    public static BuildConfiguration createBuildConfiguration(Project project, ArduinoInstallation installation, List<String> libraryNames,
                                                              String projectName, String projectLibrariesDir, List<String> preprocessorDefines,
                                                              String board) {
        def config = new BuildConfiguration()

        config.libraryNames = libraryNames
        config.projectName = projectName
        config.arduinoHome = installation.home
        config.projectDir = project.projectDir
        config.originalBuildDir = project.buildDir
        config.projectLibrariesDir = projectLibrariesDir ? new File((String)projectLibrariesDir) : null
        config.fileOperations = project
        config.board = board

        config.buildDir.mkdirs()

        def preferencesCommand = getPreferencesCommandLine(installation, config)
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

    static class Rules extends RuleSource {
        @Model
        public static void arduinoInstallation(ArduinoInstallation arduinoInstallation) {
        }

        @Defaults
        public static void defaultInstallation(ArduinoInstallation arduinoInstallation, ProjectIdentifier projectId, @Path("buildDir") File buildDir) {
            def Project project = (Project)projectId // Hack

            def String home = guessArduinoHomeIfNecessary(project)
            if (home == null) {
                log.error("No arduinoHome configured or available!")
                throw new GradleException("No arduinoHome configured or available!")
            }

            arduinoInstallation.home = home
            arduinoInstallation.packagesDir = guessArduinoPackagesDirectoryIfNecessary(home)
        }

        private static String guessArduinoPackagesDirectoryIfNecessary(String home) {
            return new File(new File(home).parent, "arduino-packages")
        }

        private static String[] arduinoHomeSearchPaths() {
            return [
                "../arduino-*/arduino-builder*",
                "arduino-*/arduino-builder*"
            ]
        }

        private static String guessArduinoHomeIfNecessary(Project project) {
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
        public static void createTasks(ModelMap<Task> tasks, ProjectIdentifier projectId, ArduinoInstallation arduinoInstallation, ArduinoBinarySpec binary) {
            def taskNameFriendlyBoardName = "-" + binary.board.replace(":", "-")
            def builder = ArduinoPlugin.createBuildConfiguration((Project)projectId, arduinoInstallation, binary.libraries, binary.projectName, null, [], binary.board)

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
