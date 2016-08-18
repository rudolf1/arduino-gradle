import org.gradle.api.*
import groovy.util.logging.Slf4j

@Slf4j
class BuildConfiguration {
    Properties preferences
    String arduinoHome
    String projectName
    String[] libraryNames
    File[] libraryPaths
    File projectDir
    File originalBuildDir
    String board

    String[] getLibrariesSearchPath() {
        return [
            "$arduinoHome/libraries"
        ];
    }

    File getBuildDir() {
        return new File(originalBuildDir, pathFriendlyBoardName)
    }

    void build() {
        log.lifecycle("Building for ${board}")

        def arduinoFiles = []
        gatherSourceFiles(arduinoFiles, new File(buildCorePath))  
        gatherSourceFiles(arduinoFiles, new File(buildVariantPath))  
        libraryPaths.each { path -> 
            gatherSourceFiles(arduinoFiles, path)  
        }

        def sketchFiles = []
        gatherSourceFiles(sketchFiles, projectDir, false)

        def sketchObjectFiles = sketchFiles.collect { buildFile(it) }
        def arduinoObjectFiles = arduinoFiles.collect { buildFile(it) }

        log.lifecycle("Archiving")

        arduinoObjectFiles.each {
            def String archiveCommand = getArCommand(it, "core.a")
            log.debug(archiveCommand)
            execute(archiveCommand)
        }

        log.lifecycle("Linking")

        def String linkCommand = getLinkCommand(sketchObjectFiles, "core.a")
        log.debug(linkCommand)
        execute(linkCommand)

        getObjCopyCommands().each {
            log.debug(it)
            execute(it)
        }
    }

    String getBuildCorePath() {
        return this.preferences."build.core.path"
    }

    String getBuildVariantPath() {
        return this.preferences."build.variant.path"
    }

    private String getKey(Properties props, String key) {
        if (props[key] == null) {
            return "{$key}"
        }
        return this.replace(props, props[key])
    }

    private String replace(Properties props, String value) {
        return value.replaceAll(/\{([\w\.-]+)\}/) { all, key ->
            return this.getKey(props, key)
        }
    }

    String getLinkCommand(objectFiles, archiveFile) {
        Properties props = new Properties()
        props["object_files"] = objectFiles.join(" ")
        props["archive_file"] = archiveFile
        props["build.project_name"] = projectName
        props.putAll(this.preferences)
        return getKey(props, "recipe.c.combine.pattern")
    }

    String getCppCommand(source, object) {
        Properties props = new Properties()
        props["source_file"] = source.toString()
        props["object_file"] = object.toString()
        props["includes"] = getIncludes().collect { "-I" + it } .join(" ")
        props.putAll(this.preferences)
        return getKey(props, "recipe.cpp.o.pattern").replace(" -c", " -x c++ -c")
    }

    String getCCommand(source, object) {
        Properties props = new Properties()
        props["source_file"] = source.toString()
        props["object_file"] = object.toString()
        props["includes"] = getIncludes().collect { "-I" + it } .join(" ")
        props.putAll(this.preferences)
        return getKey(props, "recipe.c.o.pattern")
    }

    String getArCommand(object, archive) {
        Properties props = new Properties()
        props["object_file"] = object.toString()
        props["archive_file"] = archive.toString()
        props.putAll(this.preferences)
        return getKey(props, "recipe.ar.pattern")
    }

    private boolean hasKey(String key) {
        return this.preferences[key] != null
    }

    String[] getObjCopyCommands() {
        Properties props = new Properties()
        props["build.project_name"] = projectName
        props.putAll(this.preferences)

        if (hasKey("recipe.objcopy.bin.pattern")) {
            return [
                getKey(props, "recipe.objcopy.bin.pattern"),
            ]
        }
        return [
            getKey(props, "recipe.objcopy.eep.pattern"),
            getKey(props, "recipe.objcopy.hex.pattern")
        ]
    }

    String[] getIncludes() {
        def paths = [
            buildCorePath,
            buildVariantPath
        ]

        libraryPaths.each { library ->
            paths << library.absolutePath
            library.eachDirRecurse() {
                if (!shouldSkipDirectory(it)) {
                    paths << it.absolutePath
                }
            }
        }

        return paths
    }

    String[] getLibraryPaths() {
        def libraryPaths = []
        libraryNames.each { library -> 
            log.info("Searching for $library...")
            getLibrariesSearchPath().each { librariesDir ->
                log.debug("Checking $librariesDir...")
                def libraryDirectory = new File(librariesDir, library)
                if (libraryDirectory.exists() && libraryDirectory.isDirectory()) {
                    libraryPaths << libraryDirectory
                    log.info("Found $libraryDirectory!")
                    return
                }
            }

            throw new GradleException("Unable to find " + library)
        }
        return libraryPaths;
    }

    private String getUploadTool() {
        return this.preferences."upload.tool"
    }

    private boolean isBossac() {
        return uploadTool == "bossac"
    }

    private boolean isAvrDude() {
        return uploadTool == "avrdude" || uploadTool == "arduino:avrdude"
    }

    String getUploadCommand(String serial) {
        if (this.isBossac()) {
            return getBossacUploadCommand(serial);
        }
        if (this.isAvrDude()) {
            return getAvrDudeUploadCommand(serial);
        }
        throw new GradleException("Unknown upload.tool: " + uploadTool)
    }

    boolean getUse1200BpsTouch() {
        return hasKey("upload.use_1200bps_touch")
    }

    String getUsbProduct() {
        return this.preferences["build.usb_product"].replace('"', "")
    }

    private String getBossacUploadCommand(String serial) {
        Properties props = new Properties()
        props.putAll(this.preferences)
        props["cmd"] = getKey(props, "tools.bossac.cmd")
        props["path"] = getKey(props, "tools.bossac.path")
        props["upload.verbose"] = "-i -d"
        props["serial.port.file"] = serial
        props["build.project_name"] = projectName
        return getKey(props, "tools.bossac.upload.pattern")
    }

    private String getAvrDudeUploadCommand(String serial) {
        Properties props = new Properties()
        props.putAll(this.preferences)
        props["path"] = getKey(props, "tools.avrdude.path")
        props["cmd.path"] = getKey(props, "tools.avrdude.cmd.path")
        props["config.path"] = getKey(props, "tools.avrdude.config.path")
        props["upload.verbose"] = ""
        props["upload.verify"] = "-v"
        props["serial.port"] = serial
        props["build.project_name"] = projectName
        return getKey(props, "tools.avrdude.upload.pattern")
    }

    String getPathFriendlyBoardName() {
        return board.replace(":", "-")
    }

    void gatherSourceFiles(list, dir, recurse = true) {
        dir.eachFileMatch(~/.*\.c$/) {
            list << it
        }

        dir.eachFileMatch(~/.*\.cpp$/) {
            list << it
        }

        dir.eachFileMatch(~/.*\.ino$/) {
            list << it
        }

        if (recurse) {
            dir.eachDirRecurse() {
                if (!shouldSkipDirectory(it)) {
                    gatherSourceFiles(list, it)
                }
            }
        }
    }

    private boolean shouldSkipDirectory(dir) {
        return dir.absolutePath.contains(File.separator + "examples") ||
               dir.absolutePath.contains(File.separator + ".git") ||
               dir.absolutePath.contains(File.separator + ".svn")
    }

    private File buildFile(file) {
        def objectFile = new File(buildDir, file.name + ".o")

        if (file.lastModified() < objectFile.lastModified()) {
            return objectFile
        }

        log.lifecycle("Compiling ${file.name}")

        def boolean isCpp = file.getPath() =~ /.*${File.separator}.cpp/ || file.getPath() =~ /.*${File.separator}.ino/
        def String compileCommand = isCpp ? getCppCommand(file, objectFile) : getCCommand(file, objectFile)

        log.debug(compileCommand)
        execute(compileCommand)

        return objectFile
    }

    private execute(String commandLine) {
        return RunCommand.run(commandLine, projectDir)
    }
}
