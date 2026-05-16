package org.kikd.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

data class GeneratedFile(
    val relativePath: String,
    val content: String,
) {
    init {
        require(relativePath.isNotBlank()) { "Generated file path cannot be blank." }
        require(!Path.of(relativePath).isAbsolute) { "Generated file path must be relative: $relativePath" }
        require(!relativePath.contains("..")) { "Generated file path cannot traverse directories: $relativePath" }
    }
}

interface GeneratorBackend {
    val id: String

    fun generate(project: KikdProject): List<GeneratedFile>
}

fun generate(
    project: KikdProject,
    vararg backends: GeneratorBackend,
): List<Path> = generate(project, Path.of("output"), backends.asIterable())

fun generate(
    project: KikdProject,
    backends: Iterable<GeneratorBackend>,
): List<Path> = generate(project, Path.of("output"), backends)

fun generate(
    project: KikdProject,
    outputDir: Path,
    vararg backends: GeneratorBackend,
): List<Path> = generate(project, outputDir, backends.asIterable())

fun generate(
    project: KikdProject,
    outputDir: Path,
    backends: Iterable<GeneratorBackend>,
): List<Path> {
    val root = outputDir.toAbsolutePath().normalize()
    Files.createDirectories(root)
    require(root.isDirectory()) { "Output path is not a directory: $root" }

    val plan = KikdProjectPlanner.plan(project)
    val files = backends.flatMap { backend ->
        if (backend is PlanAwareGeneratorBackend) {
            backend.generate(plan)
        } else {
            backend.generate(project)
        }
    }.sortedBy { it.relativePath }
    return files.map { file ->
        val target = root.resolve(file.relativePath).normalize()
        require(target.startsWith(root)) { "Generated file escaped output directory: ${file.relativePath}" }
        Files.createDirectories(target.parent)
        Files.writeString(target, file.content.ensureTrailingNewline())
        target
    }
}

private fun String.ensureTrailingNewline(): String =
    if (endsWith("\n")) this else "$this\n"
