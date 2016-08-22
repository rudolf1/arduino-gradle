package com.github.jlewallen.arduino;

class ArduinoPluginExtension {
    def String projectName
    def String defaultBoard
    def String[] boards = []
    def String projectLibrariesDir
    def String arduinoPackagesDir
    def String[] libraries = []
    def boolean provideMain = true
    def String[] preprocessorDefines = []
    def String home
}