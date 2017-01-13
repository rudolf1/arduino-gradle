package com.github.jlewallen.arduino

import org.conservify.firmwaretool.util.DoubleQuotedArgumentsOnWindowsCommandLine;
import org.conservify.firmwaretool.util.CommandLineParser
import org.gradle.api.*
import groovy.util.logging.Slf4j

@Slf4j
class RunCommand {
    static String run(String cmd, File workingDir, boolean echo = false) {
        def split = CommandLineParser.translateCommandLine(cmd)
        def fixed = split.collect { DoubleQuotedArgumentsOnWindowsCommandLine.fixArgument(it) }
        return run(fixed as String[], workingDir, echo)
    }

    static String run(String[] cmd, File workingDir, boolean echo = false) {
        def so = new StringBuffer()
        def se = new StringBuffer()

        log.info(cmd.join(" "))

        def process = Runtime.runtime.exec(cmd as String[], [] as String[], workingDir)
        def running = true

        process.getOutputStream().close()

        def bufferPrinter = { buffer ->
            def lastIndex = 0
            while (running) {
                def length = buffer.length()
                if (length > lastIndex) {
                    // print buffer.subSequence(lastIndex, length)
                    lastIndex = length
                }
                Thread.sleep(100)
            }
        }
        Thread.start bufferPrinter.curry(so)
        Thread.start bufferPrinter.curry(se)

        process.consumeProcessOutput(so, se)

        try {
            process.waitFor()
        } finally {
            running = false
        }

        if (process.exitValue()) {
            throw new GradleException("Command execution failed (Exit code: ${process.exitValue()})\n${so.toString()}\n${se.toString()}")
        }

        return so.toString()
    }
}
