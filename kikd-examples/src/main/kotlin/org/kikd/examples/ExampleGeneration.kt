package org.kikd.examples

import org.kikd.core.GeneratorBackend
import org.kikd.core.KikdProject
import org.kikd.core.generate
import java.nio.file.Path

private val examplesRoot: Path = Path.of("kikd-examples/output")

internal fun generateExample(
    folderName: String,
    project: KikdProject,
    vararg backends: GeneratorBackend,
) {
    generate(
        project = project,
        outputDir = examplesRoot.resolve(folderName),
        backends = backends.asIterable(),
    )
}
