package com.github.jlewallen.arduino;

import groovy.util.logging.Slf4j
import org.conservify.builds.gitdeps.BuildDependencies
import org.conservify.firmwaretool.uploading.UploaderConfig
import org.gradle.api.*
import org.gradle.api.internal.file.FileOperations
import groovy.json.JsonOutput
import groovy.json.JsonBuilder
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
class BuildConfiguration {
    BuildDependencies buildDependencies = new BuildDependencies()
    Properties preferences
    String arduinoHome
    String projectName
    String[] libraryNames
    String[] additionalSources
    String userCppSourcesFlags
    String userCSourcesFlags
    File[] libraryPaths
    File projectDir
    File originalBuildDir
    File projectLibrariesDir
    File buildDir
    String board
    boolean provideMain
    boolean wasAnythingCompiled
    boolean isLibrary
    FileOperations fileOperations

    File[] getLibrariesSearchPath() {
        def paths = [
            new File("$arduinoHome/libraries")
        ]

        if (this.projectLibrariesDir) {
            paths << this.projectLibrariesDir
        }

        def runtimePlatformTree = fileOperations.fileTree(new File(this.preferences."runtime.platform.path"))
        runtimePlatformTree.visit { details ->
            if (details.file.name == 'libraries') {
                paths << details.file
            }
        }

        // There has to be a better way, this breaks for other cores though and
        // for the feather32u4 to get the AVR SoftwareSerial it's necessary. I'm sure
        // I'll stumble upon a better way eventually.
        if (buildVariant == "feather32u4") {
            def buildCoreTree = fileOperations.fileTree(new File(arduinoHome, "hardware/" + buildCore))
            buildCoreTree.visit { details ->
                if (details.file.name == 'libraries') {
                    paths << details.file
                }
            }
        }

        return paths;
    }

    File[] findProjectFiles() {
        def projectFiles = []
        gatherSourceFiles(projectFiles, projectDir, false, false)
        additionalSources.each { path ->
            gatherSourceFiles(projectFiles, fileOperations.file(path), false, false)
        }
        if (projectFiles.size() == 0) {
          throw new GradleException("No project files found in $projectDir")
        }
        provideMain = anyInoOrPdeFiles(projectFiles)
        return projectFiles
    }

    File[] findNonProjectFiles() {
        if (isLibrary) {
            return []
        }

        return findCoreAndVariantFiles() + findLibraryFiles()
    }

    File[] findCoreAndVariantFiles() {
        def nonProjectFiles = []
        gatherSourceFiles(nonProjectFiles, new File(buildCorePath), true, true) {
            if (provideMain)
                return true
            return !(it.name =~ /main.(c|cpp)/)
        }

        gatherSourceFiles(nonProjectFiles, new File(buildVariantPath), true, true)

        return nonProjectFiles
    }

    File[] findLibraryFiles() {
        def libraryFiles = []
        this.getLibraryPaths().each { path ->
            gatherSourceFiles(libraryFiles, path, true, true)
        }
        return libraryFiles
    }

    File getArchiveFile() {
        if (isLibrary) {
            return new File(buildDir, projectName + ".a")
        }
        return new File(buildDir, "core.a")
    }

    File[] getAllObjectFiles() {
        def sourceFiles = getAllSourceFiles()
        return sourceFiles.collect { makeObjectFilePath(it) }.unique()
    }

    File[] getAllSourceFiles() {
        def projectFiles = findProjectFiles()
        def nonProjectFiles = findNonProjectFiles()
        return projectFiles + nonProjectFiles
    }

    File getBinaryFile() {
        Properties props = new Properties()
        props["build.project_name"] = projectName
        props.putAll(preferences)
        if (isAvrDude()) {
            return new File(replace(props, "{build.path}/{build.project_name}.hex"))
        }
        if (isBossac()) {
            return new File(replace(props, "{build.path}/{build.project_name}.bin"))
        }
        throw new GradleException("Unable to get binary file")
    }

    File getMetaFile() {
        Properties props = new Properties()
        props["build.project_name"] = projectName
        props.putAll(preferences)
        return new File(replace(props, "{build.path}/{build.project_name}.json"))
    }

    File writeMetaFile() {
        File file = getMetaFile()

        def props = new Properties()
        props.putAll(preferences)

        def json = new JsonBuilder()
        json.meta {
            variant(getKey(props, "build.variant"))
            project(projectName)
            board(board)
            time(getKey(props, "extra.time.utc"))
            upload {
                tool(uploadTool)
                use1200bpsTouch this.hasKey("upload.use_1200bps_touch")
                command this.getUploadCommandTemplate()
            }
        }

        file.withWriter { o ->
            o.print(JsonOutput.prettyPrint(json.toString()))
        }

        return file
    }

    File linkArchive(File[] objectFiles, File archive) {
        objectFiles.each {
            def String archiveCommand = getArCommand(it, archive.name)
            log.debug(archiveCommand)
            execute(archiveCommand)
        }
        return archive
    }

    File linkBinary(File[] objectFiles, File archive)  {
        def String linkCommand = getLinkCommand(objectFiles, archive.name)
        log.debug(linkCommand)
        execute(linkCommand)

        getObjCopyCommands().each {
            log.debug(it)
            execute(it)
        }

        writeMetaFile()

        return binaryFile
    }

    void build() {
        log.lifecycle("Building for ${board}")

        def projectFiles = findProjectFiles()
        def provideMain = anyInoOrPdeFiles(projectFiles)

        def nonProjectFiles = findNonProjectFiles(provideMain)

        def projectObjectFiles = projectFiles.collect { buildFile(it) }
        def nonProjectObjectFiles = nonProjectFiles.collect { buildFile(it) }

        if (wasAnythingCompiled) {
            log.lifecycle("Archiving")
            linkArchive(nonProjectObjectFiles, getArchiveFile())

            log.lifecycle("Linking")
            linkBinary(projectObjectFiles, getArchiveFile())
        }
    }

    String getBuildCore() {
        return this.preferences."build.core"
    }

    String getBuildCorePath() {
        return this.preferences."build.core.path"
    }

    String getBuildVariant() {
        return this.preferences."build.variant"
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
        props["archive_file_path"] = new File(buildDir, archiveFile).toString()
        props["build.project_name"] = projectName
        props.putAll(this.preferences)
        return getKey(props, "recipe.c.combine.pattern")
    }

    String getCppCommand(source, object, isUserFile, isCoreFile, isLibraryFile) {
        Properties props = new Properties()
        props.putAll(this.preferences)
        props["source_file"] = source.toString()
        props["object_file"] = object.toString()
        props["includes"] = getIncludes(isCoreFile, isLibraryFile).collect { "-I" + it } .join(" ")
        if (isUserFile) {
            props["compiler.cpp.extra_flags"] = [props["compiler.cpp.extra_flags"], userCppSourcesFlags].findAll().join(" ")
        }
        return getKey(props, "recipe.cpp.o.pattern").replace(" -c", " -x c++ -c")
    }

    String getCCommand(source, object, isUserFile, isCoreFile, isLibraryFile) {
        Properties props = new Properties()
        props.putAll(this.preferences)
        props["source_file"] = source.toString()
        props["object_file"] = object.toString()
        props["includes"] = getIncludes(isCoreFile, isLibraryFile).collect { "-I" + it } .join(" ")
        if (isUserFile) {
            props["compiler.c.extra_flags"] = [props["compiler.c.extra_flags"],  userCSourcesFlags].findAll().join(" ")
        }
        return getKey(props, "recipe.c.o.pattern")
    }

    String getArCommand(object, archive) {
        Properties props = new Properties()
        props["object_file"] = object.toString()
        props["archive_file"] = archive.toString()
        props["archive_file_path"] = new File(buildDir, archive).toString()
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

    String[] getIncludes(isCoreFile, isLibraryFile) {
        def paths = [ ]

        if (!isCoreFile) {
            this.getLibraryPaths().each { library ->
                paths << library.absolutePath
                library.eachDirRecurse() {
                    if (!shouldSkipDirectory(it)) {
                        paths << it.absolutePath
                    }
                }
            }
            additionalSources.each { path ->
                paths << path
            }
        }

        paths << buildCorePath
        paths << buildVariantPath

        return paths
    }

    List<File> cachedLibraryPaths

    File[] getLibraryPaths() {
        if (cachedLibraryPaths == null) {
            cachedLibraryPaths = []
            this.libraryNames.each { library ->
                def File libraryDirectory = buildDependencies.locate(librariesSearchPath, library)
                if (libraryDirectory != null) {
                    cachedLibraryPaths << libraryDirectory
                }
                else {
                    throw new GradleException("Unable to find " + library)
                }
            }
        }
        return cachedLibraryPaths;
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
            return getBossacUploadCommand(serial)
        }
        if (this.isAvrDude()) {
            return getAvrDudeUploadCommand(serial)
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

    private String getUploadCommandTemplate() {
        Properties props = new Properties()
        props.putAll(this.preferences)
        props.remove("build.path")
        if (this.isBossac()) {
            return getKey(props, "tools.bossac.upload.pattern")
        }
        if (this.isAvrDude()) {
            Properties newProps = new Properties()
            newProps.putAll(this.preferences)
            newProps["path"] = getKey(newProps, "tools.avrdude.path")
            newProps["cmd.path"] = getKey(newProps, "tools.avrdude.cmd.path")
            newProps["config.path"] = getKey(newProps, "tools.avrdude.config.path")
            newProps["upload.verbose"] = ""
            newProps["upload.verify"] = "-v"
            return getKey(newProps, "tools.avrdude.upload.pattern")
        }
        throw new GradleException("Unknown upload.tool: " + uploadTool)
    }

    boolean anyInoOrPdeFiles(files) {
        return files.findAll { it.name =~ /.*\.(ino|pde)/ }.size() > 0
    }

    void gatherSourceFiles(ArrayList list, File dir, boolean recurse = true, boolean forLibrary = false, Closure closure = { f -> true }) {
        dir.eachFileMatch(~/.*\.c$/) {
            if (closure(it)) {
                list << it
            }
        }

        dir.eachFileMatch(~/.*\.cpp$/) {
            if (closure(it)) {
                list << it
            }
        }

        if (!forLibrary) {
            dir.eachFileMatch(~/.*\.ino$/) {
                if (closure(it)) {
                    list << it
                }
            }
        }

        if (recurse) {
            dir.eachDirRecurse() {
                if (!shouldSkipDirectory(it)) {
                    gatherSourceFiles(list, it, recurse, forLibrary)
                }
            }
        }
    }

    private boolean shouldSkipDirectory(File dir) {
        Path pathBase = projectDir.toPath()
        Path pathDir = dir.toPath()
        Path relative = pathBase.relativize(pathDir)
        String path = relative.toString()

        return path.contains(File.separator + "examples") ||
               path.contains(File.separator + ".git") ||
               path.contains(File.separator + ".svn") ||
               path.contains(File.separator + "tests") ||
               path.contains(File.separator + "test")
    }

    UploaderConfig getUploadConfig() {
        def config = new UploaderConfig()
        config.commandLine = this.getUploadCommandTemplate()
        if (this.isBossac()) {
            config.toolsPath = new File(getKey(this.preferences, "tools.bossac.path"))
        }
        if (this.isAvrDude()) {
            config.toolsPath = new File(getKey(this.preferences, "tools.avrdude.cmd.path"))
        }
        config.use1200bpsTouch = use1200BpsTouch
        return config
    }

    File makeObjectFilePath(File file) {
        return new File(buildDir, file.name + ".o")
    }

    File buildFile(file) {
        def objectFile = makeObjectFilePath(file)

        // My gradle foo isn't good so for some reason doing a 'clean build' makes this necessary.
        new File(objectFile.getParent()).mkdirs()

        if (file.lastModified() < objectFile.lastModified()) {
            return objectFile
        }

        log.lifecycle("Compiling ${file.name}")

        def boolean isCpp = file.getPath() =~ /.*\.cpp/ || file.getPath() =~ /.*\.ino/
        def boolean isCoreFile = findCoreAndVariantFiles().contains(file)
        def boolean isLibraryFile = findLibraryFiles().contains(file)
        def boolean isUserFile = file.toString().startsWith(projectDir.toString())
        def String compileCommand = isCpp ? getCppCommand(file, objectFile, isUserFile, isCoreFile, isLibraryFile) : getCCommand(file, objectFile, isUserFile, isCoreFile, isLibraryFile)

        log.debug(compileCommand)
        execute(compileCommand)

        wasAnythingCompiled = true

        return objectFile
    }

    private execute(String commandLine) {
        return RunCommand.run(commandLine, projectDir)
    }
}
