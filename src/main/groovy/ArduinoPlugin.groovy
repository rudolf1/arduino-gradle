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

            def File dir = new File(System.getProperty("user.dir"))
            try {
                while (dir != null) {
                    def tree = project.fileTree(dir)
                    def found = tree.include("arduino-*/arduino-builder*").files.unique().sort()
                    if (found.size() == 1) {
                        return found[0].parent
                    }
                    else if (found.size() > 1) {
                        println found
                    }

                    dir = dir.getParentFile()
                }
            }
            catch (Exception e) {
                log.error("Unable to locate ArduinoHome", e)
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
            component.boards.each { board ->
                binaries.create("exploded") { binary ->
                    binary.board = board
                    binary.libraries = component.libraries
                    binary.projectName = component.name
                }
            }
        }

        private static String guessProjectLibrariesDirectory(Project project) {
            println project
            def File dir = project.buildDir
            while (dir != null) {
                def File libs = new File(dir, "libraries")
                if (libs.isDirectory()) {
                    log.info("Using ${libs}")
                    return libs
                }
                dir = dir.getParentFile()
            }
            return null;
        }

        @Mutate
        public static void createUploadTasks(ModelMap<Task> tasks, ProjectIdentifier projectId, ArduinoInstallation arduinoInstallation, ModelMap<ArduinoComponentSpec> components) {
            def project = (Project)projectId
            def uploadTaskNames = []
            def monitorTaskNames = []

            components.each { component ->
                component.boards.each { board ->
                    def taskNameFriendlyBoardName = "-" + board.replace(":", "-")
                    def builder = createBuildConfiguration(project, arduinoInstallation, [], component.name, null, [], board)
                    def uploadTaskName = "upload" + taskNameFriendlyBoardName
                    def monitorTaskName = "monitor" + taskNameFriendlyBoardName

                    tasks.create(uploadTaskName, UploadTask.class, { task ->
                        task.outputs.upToDateWhen { false }
                        if (project.hasProperty("port")) {
                            task.port = project.port
                        }
                        task.buildConfiguration = builder
                        task.dependsOn("build")
                    })

                    tasks.create(monitorTaskName, MonitorTask.class, { task ->
                        task.outputs.upToDateWhen { false }
                        task.uploadTask = tasks.get(uploadTaskName)
                        task.dependsOn("upload")
                    })

                    uploadTaskNames << uploadTaskName
                    monitorTaskNames << monitorTaskName
                }
            }

            tasks.create("upload", DefaultTask.class, { top ->
                uploadTaskNames.each { top.dependsOn(it) }
            })

            tasks.create("monitor", DefaultTask.class, { top ->
                monitorTaskNames.each { top.dependsOn(it) }
            })
        }

        @BinaryTasks
        public static void createTasks(ModelMap<Task> tasks, ProjectIdentifier projectId, ArduinoInstallation arduinoInstallation, ArduinoBinarySpec binary) {
            def taskNameFriendlyBoardName = "-" + binary.board.replace(":", "-")
            def project = (Project)projectId
            def builder = createBuildConfiguration(project, arduinoInstallation, binary.libraries, binary.projectName, guessProjectLibrariesDirectory(project), [], binary.board)

            def compileTaskName = binary.tasks.taskName("compile", taskNameFriendlyBoardName)
            def archiveTaskName = binary.tasks.taskName("archive", taskNameFriendlyBoardName)
            def linkTaskName = binary.tasks.taskName("link", taskNameFriendlyBoardName)

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

                task.objectFiles.each { task.inputs.file(it) }
                task.inputs.file(task.archiveFile)
                task.outputs.file(task.binary)
            })
        }

        private static String[] getPreferencesCommandLine(ArduinoInstallation installation, String projectLibrariesDir, String board, File buildDir) {
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

            if (projectLibrariesDir) {
                parts.add("-libraries")
                parts.add(projectLibrariesDir)
            }

            parts.addAll(["-fqbn=${board}",
                        "-ide-version=10609",
                        "-build-path",
                        "${buildDir}",
                        "-warnings=none",
                        "-quiet"])
            return parts.toArray()
        }

        private static BuildConfiguration createBuildConfiguration(Project project, ArduinoInstallation installation, List<String> libraryNames,
                                                                   String projectName, String projectLibrariesDir, List<String> preprocessorDefines,
                                                                   String board) {
            def String pathFriendlyBoardName = board.replace(":", "-")
            def File buildDir = new File(project.buildDir, pathFriendlyBoardName)
            buildDir.mkdirs();

            def preferencesCommand = getPreferencesCommandLine(installation, projectLibrariesDir, board, buildDir)
            def String data = RunCommand.run(preferencesCommand, project.projectDir)

            /*
            def friendlyName = config.pathFriendlyBoardName
            def localCopy = new File(project.buildDir, "arduino.${friendlyName}.prefs")
            localCopy.write(data)
            */
            def preferences = new Properties()
            preferences.load(new StringReader(data.replace("\\", "\\\\")))

            def extraFlags = preprocessorDefines.collect { "-D" + it }.join(" ")
            preferences["compiler.c.extra_flags"] += " " + extraFlags
            preferences["compiler.cpp.extra_flags"] += " " + extraFlags

            def config = new BuildConfiguration()

            config.libraryNames = libraryNames
            config.projectName = projectName
            config.arduinoHome = installation.home
            config.projectDir = project.projectDir
            config.originalBuildDir = project.buildDir
            config.projectLibrariesDir = projectLibrariesDir ? new File((String)projectLibrariesDir) : null
            config.fileOperations = project
            config.board = board
            config.preferences = preferences
            config.buildDir = buildDir
            config.buildDir.mkdirs()

            return config
        }
    }
}
