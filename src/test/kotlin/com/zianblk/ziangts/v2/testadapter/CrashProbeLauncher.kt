package com.zianblk.ziangts.v2.testadapter

import java.nio.file.Files
import java.nio.file.Path

/** An argument file keeps crash probes runnable on Windows with NeoForge's long test classpath. */
internal fun launchCrashProbe(dir: Path, mainClass: Class<*>, vararg arguments: String): Process {
    val java = Path.of(System.getProperty("java.home"), "bin",
        if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
    val argsFile = Files.createTempFile(dir, "crash-probe-", ".args")
    val args = listOf("-cp", System.getProperty("java.class.path"), mainClass.name) + arguments
    Files.writeString(argsFile, args.joinToString("\n") { arg ->
        "\"${arg.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    })
    return ProcessBuilder(java.toString(), "@${argsFile}").redirectErrorStream(true).start()
}
