package com.github.jlewallen.arduino;

import org.gradle.model.Managed
import org.gradle.platform.base.BinarySpec

@Managed
interface ArduinoBinarySpec extends BinarySpec {
    void setProjectName(String name)
    String getProjectName()

    void setLibraries(List<String> libraries)
    List<String> getLibraries()

    void setBoard(String board)
    String getBoard()

    String getUserCppSourcesFlags()
    void setUserCppSourcesFlags(String flags)

    String getUserCSourcesFlags()
    void setUserCSourcesFlags(String flags)
}
