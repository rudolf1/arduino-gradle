package com.github.jlewallen.arduino;

import org.gradle.model.Managed;
import org.gradle.platform.base.GeneralComponentSpec;

import java.util.List;

@Managed
public interface ArduinoLibrarySpec extends GeneralComponentSpec {
    void setBoards(List<String> boards)
    List<String> getBoards()

    void setLibraries(List<String> libraries)
    List<String> getLibraries()

    String getUserCppSourcesFlags()
    void setUserCppSourcesFlags(String flags)

    String getUserCSourcesFlags()
    void setUserCSourcesFlags(String flags)
}
