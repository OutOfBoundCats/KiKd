package org.kikd.azure.pipelines

import org.kikd.azure.azure
import org.kikd.core.GeneratedFile
import org.kikd.core.KikdProjectPlan
import org.kikd.core.PlanAwareGeneratorBackend

class AzurePipelinesYamlBackend(
    private val baseDirectory: String = ".azure-pipeline",
    private val pipelineFileName: String = "azure-pipelines.yml",
) : PlanAwareGeneratorBackend {
    override val id: String = "azure-pipelines-yaml"

    override fun generate(plan: KikdProjectPlan): List<GeneratedFile> {
        val pipeline = plan.project.azure()?.pipeline() ?: return emptyList()
        val files = mutableListOf<GeneratedFile>()
        val root = linkedMapOf<String, Any?>()
        pipeline.name?.let { root["name"] = it }
        pipeline.trigger?.toYamlValue()?.let { root["trigger"] = it }
        if (pipeline.parameters.isNotEmpty()) root["parameters"] = pipeline.parameters.map { it.toYamlValue() }
        if (pipeline.variables.isNotEmpty()) root["variables"] = pipeline.variables.map { it.toYamlValue() }
        if (pipeline.resources.isNotEmpty()) root["resources"] = pipeline.resources
        root.putAll(pipeline.extra)
        if (pipeline.stages.isNotEmpty()) {
            root["stages"] = stageTemplates(pipeline.stages, files)
        }
        files += GeneratedFile("$baseDirectory/pipelines/$pipelineFileName", YamlWriter.write(root))
        return files.sortedBy { it.relativePath }
    }

    private fun stageTemplates(
        stages: List<AzurePipelineStage>,
        files: MutableList<GeneratedFile>,
    ): List<Map<String, Any?>> {
        val usedStageFiles = mutableSetOf<String>()
        return stages.mapIndexed { stageIndex, stage ->
            val stageValue = stage.toYamlValue()
            val stageId = stageValue["stage"]?.toString() ?: "stage-${stageIndex + 1}"
            val stageFile = uniqueFileName(slug(stageId), usedStageFiles)
            val jobs = stageValue["jobs"].asMapList()
            val stageWithTemplates = linkedMapOf<String, Any?>().also { out ->
                out.putAll(stageValue)
                out["jobs"] = jobTemplates(stageId, jobs, files)
            }
            files += GeneratedFile(
                "$baseDirectory/stages/$stageFile",
                YamlWriter.write(linkedMapOf("stages" to listOf(stageWithTemplates))),
            )
            linkedMapOf("template" to "../stages/$stageFile")
        }
    }

    private fun jobTemplates(
        stageId: String,
        jobs: List<Map<String, Any?>>,
        files: MutableList<GeneratedFile>,
    ): List<Map<String, Any?>> {
        val usedStepFiles = mutableSetOf<String>()
        return jobs.mapIndexed { jobIndex, job ->
            val jobId = job["job"]?.toString() ?: "job-${jobIndex + 1}"
            val stepFile = uniqueFileName("${slug(stageId)}-${slug(jobId)}", usedStepFiles)
            val steps = job["steps"].asMapList()
            files += GeneratedFile(
                "$baseDirectory/steps/$stepFile",
                YamlWriter.write(linkedMapOf("steps" to steps)),
            )
            linkedMapOf<String, Any?>().also { out ->
                out.putAll(job)
                out["steps"] = listOf(linkedMapOf("template" to "../steps/$stepFile"))
            }
        }
    }

    private fun Any?.asMapList(): List<Map<String, Any?>> =
        (this as? Iterable<*>)?.mapNotNull { value ->
            (value as? Map<*, *>)?.entries?.associateTo(linkedMapOf()) { (key, entryValue) ->
                key.toString() to entryValue
            }
        } ?: emptyList()

    private fun uniqueFileName(base: String, used: MutableSet<String>): String {
        var candidate = "$base.yml"
        var counter = 2
        while (!used.add(candidate)) {
            candidate = "$base-$counter.yml"
            counter += 1
        }
        return candidate
    }

    private fun slug(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unnamed" }
}

internal object YamlWriter {
    fun write(value: Any?): String = buildString {
        appendValue(value, indent = 0, root = true)
    }.trimEnd()

    private fun StringBuilder.appendValue(value: Any?, indent: Int, root: Boolean = false) {
        when (value) {
            null -> append("null")
            is Map<*, *> -> appendMap(value, indent, root)
            is Iterable<*> -> appendList(value, indent)
            is Array<*> -> appendList(value.asList(), indent)
            is Boolean, is Number -> append(value.toString())
            else -> appendScalar(value.toString())
        }
    }

    private fun StringBuilder.appendMap(map: Map<*, *>, indent: Int, root: Boolean) {
        if (map.isEmpty()) {
            append("{}")
            return
        }
        if (!root) append('\n')
        map.entries.forEachIndexed { index, (key, value) ->
            if (index > 0 || !root) appendIndent(indent)
            append(key.toString()).append(':')
            appendNested(value, indent)
        }
    }

    private fun StringBuilder.appendList(values: Iterable<*>, indent: Int) {
        val list = values.toList()
        if (list.isEmpty()) {
            append("[]")
            return
        }
        append('\n')
        list.forEach { value ->
            appendIndent(indent)
            append("-")
            appendNested(value, indent)
        }
    }

    private fun StringBuilder.appendNested(value: Any?, indent: Int) {
        when (value) {
            is Map<*, *> -> appendValue(value, indent + 2)
            is Iterable<*> -> appendValue(value, indent + 2)
            is Array<*> -> appendValue(value.asList(), indent + 2)
            else -> {
                append(' ')
                appendValue(value, indent + 2)
                append('\n')
            }
        }
    }

    private fun StringBuilder.appendScalar(value: String) {
        val plain = value.isNotBlank() &&
            value.none { it == '\n' || it == '\r' } &&
            !value.first().isWhitespace() &&
            !value.last().isWhitespace() &&
            !setOf(':', '{', '}', '[', ']', ',', '&', '*', '#', '?', '|', '-', '<', '>', '=', '!', '%', '@').contains(value.first())

        if (plain && value.lowercase() !in setOf("null", "true", "false")) {
            append(value)
        } else {
            append('\'').append(value.replace("'", "''")).append('\'')
        }
    }

    private fun StringBuilder.appendIndent(indent: Int) {
        repeat(indent) { append(' ') }
    }
}
