package org.kikd.azure.pipelines

import org.kikd.azure.AzureComponent
import org.kikd.azure.AzureGraphContributor
import org.kikd.core.KikdDependencyKind
import org.kikd.core.KikdGraphBuilder
import org.kikd.core.KikdGraphNodeKind
import org.kikd.core.graphIdSegment

data class AzurePipelineComponent(
    val pipeline: AzurePipeline,
) : AzureComponent, AzureGraphContributor {
    override fun contributeAzureGraph(builder: KikdGraphBuilder, cloudNodeId: String) {
        val pipelineId = "$cloudNodeId:pipeline"
        builder.node(
            id = pipelineId,
            kind = KikdGraphNodeKind.PIPELINE,
            label = "Azure Pipeline",
            parentId = cloudNodeId,
            payload = pipeline,
        )

        val artifactIds = mutableMapOf<String, String>()
        fun artifactId(artifact: PipelineArtifactRef): String =
            artifactIds.getOrPut(artifact.name) {
                "$cloudNodeId:artifact:${graphIdSegment(artifact.name)}".also { id ->
                    builder.node(
                        id = id,
                        kind = KikdGraphNodeKind.ARTIFACT,
                        label = artifact.name,
                        parentId = cloudNodeId,
                        payload = artifact,
                    )
                }
            }

        pipeline.stages.forEach { stage ->
            val stageId = stageNodeId(pipelineId, stage.id)
            builder.node(
                id = stageId,
                kind = KikdGraphNodeKind.STAGE,
                label = stage.id,
                parentId = pipelineId,
                payload = stage,
            )
            stage.dependencyIds().forEach { dependency ->
                builder.dependsOn(stageId, stageNodeId(pipelineId, dependency), KikdDependencyKind.STAGE_DEPENDS_ON)
            }

            stage.jobs.forEach { job ->
                val jobId = jobNodeId(stageId, job.id)
                builder.node(
                    id = jobId,
                    kind = KikdGraphNodeKind.JOB,
                    label = job.id,
                    parentId = stageId,
                    payload = job,
                )
                job.dependencyIds().forEach { dependency ->
                    builder.dependsOn(jobId, jobNodeId(stageId, dependency), KikdDependencyKind.JOB_DEPENDS_ON)
                }

                job.steps.forEachIndexed { index, step ->
                    val stepId = "$jobId:step:${index + 1}"
                    builder.node(
                        id = stepId,
                        kind = KikdGraphNodeKind.STEP,
                        label = step.displayName ?: step.kind,
                        parentId = jobId,
                        payload = step,
                    )
                    step.producesArtifacts.forEach { artifact ->
                        builder.dependsOn(
                            dependentId = artifactId(artifact),
                            dependencyId = stepId,
                            kind = KikdDependencyKind.ARTIFACT_PRODUCED_BY,
                        )
                    }
                    step.consumesArtifacts.forEach { artifact ->
                        builder.dependsOn(
                            dependentId = stepId,
                            dependencyId = artifactId(artifact),
                            kind = KikdDependencyKind.ARTIFACT_CONSUMED_BY,
                        )
                    }
                    step.deploysStacks.forEach { stack ->
                        builder.dependsOn(
                            dependentId = stepId,
                            dependencyId = "$cloudNodeId:infrastructure:stack:${graphIdSegment(stack.name)}",
                            kind = KikdDependencyKind.STEP_DEPENDS_ON,
                        )
                    }
                }
            }
        }
    }

    private fun stageNodeId(pipelineId: String, stageId: String): String =
        "$pipelineId:stage:${graphIdSegment(stageId)}"

    private fun jobNodeId(stageNodeId: String, jobId: String): String =
        "$stageNodeId:job:${graphIdSegment(jobId)}"
}

data class AzurePipeline(
    val name: String?,
    val trigger: Trigger?,
    val parameters: List<PipelineParameter>,
    val variables: List<PipelineVariableEntry>,
    val resources: Map<String, Any?>,
    val stages: List<AzurePipelineStage>,
    val extra: Map<String, Any?>,
)

data class PipelineStage(
    val id: String,
)

data class PipelineJob(
    val id: String,
)

data class PipelineArtifactRef(
    val name: String,
    val path: String,
)

data class InfrastructureStackRef(
    val name: String,
)

data class PipelineParameter(
    val name: String,
    val type: String,
    val default: Any? = null,
    val values: List<Any?> = emptyList(),
) {
    fun macroReference(): String = "\$($name)"

    fun toYamlValue(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "name" to name,
        "type" to type,
    ).also {
        if (default != null) it["default"] = default
        if (values.isNotEmpty()) it["values"] = values
    }
}

sealed interface PipelineVariableEntry {
    fun toYamlValue(): Map<String, Any?>
}

data class PipelineVariable(
    val name: String,
    val value: Any?,
    val isSecret: Boolean = false,
) : PipelineVariableEntry {
    override fun toYamlValue(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "name" to name,
        "value" to value,
    ).also {
        if (isSecret) it["isSecret"] = true
    }
}

data class PipelineVariableGroup(
    val name: String,
) : PipelineVariableEntry {
    override fun toYamlValue(): Map<String, Any?> = linkedMapOf("group" to name)

    fun variable(name: String): PipelineVariableReference = PipelineVariableReference(name)
}

data class PipelineVariableReference(
    val name: String,
) {
    fun macroReference(): String = "\$($name)"
}

class PipelineStageOutputVariable internal constructor(
    val name: String,
) {
    private var producer: PipelineStageOutputProducer? = null

    internal fun assignFrom(stageId: String, jobId: String, stepName: String, outputName: String) {
        val nextProducer = PipelineStageOutputProducer(stageId, jobId, stepName, outputName)
        check(producer == null || producer == nextProducer) {
            "Stage output variable '$name' is already assigned by ${producer!!.description()}."
        }
        producer = nextProducer
    }

    internal fun stageDependencyExpression(): String {
        val assignedProducer = producer ?: error(
            "Stage output variable '$name' has not been assigned by a named step before it is used.",
        )
        return "\$[ stageDependencies.${assignedProducer.stageId}.${assignedProducer.jobId}.outputs['${assignedProducer.stepName}.${assignedProducer.outputName}'] ]"
    }
}

private data class PipelineStageOutputProducer(
    val stageId: String,
    val jobId: String,
    val stepName: String,
    val outputName: String,
) {
    fun description(): String = "$stageId.$jobId.$stepName.$outputName"
}

class PipelineStageOutputBinding internal constructor(
    val variable: PipelineStageOutputVariable,
    val outputName: String,
)

data class AzurePipelineStage(
    val id: String,
    val displayName: String?,
    val condition: String?,
    val dependsOn: Any?,
    val variables: List<PipelineVariableEntry>,
    val jobs: List<AzurePipelineJob>,
    val extra: Map<String, Any?>,
) {
    fun dependencyIds(): List<String> = dependencyIds(dependsOn, PipelineStage::class.java)

    fun toYamlValue(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "stage" to id,
    ).also { out ->
        displayName?.let { out["displayName"] = it }
        dependsOn?.let { out["dependsOn"] = it }
        condition?.let { out["condition"] = it }
        if (variables.isNotEmpty()) out["variables"] = variables.map { it.toYamlValue() }
        out["jobs"] = jobs.map { it.toYamlValue() }
        out.putAll(extra)
    }
}

data class AzurePipelineJob(
    val id: String,
    val displayName: String?,
    val condition: String?,
    val dependsOn: Any?,
    val pool: Any?,
    val variables: List<PipelineVariableEntry>,
    val steps: List<AzurePipelineStep>,
    val extra: Map<String, Any?>,
) {
    fun dependencyIds(): List<String> = dependencyIds(dependsOn, PipelineJob::class.java)

    fun toYamlValue(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "job" to id,
    ).also { out ->
        displayName?.let { out["displayName"] = it }
        dependsOn?.let { out["dependsOn"] = it }
        condition?.let { out["condition"] = it }
        pool?.let { out["pool"] = it }
        if (variables.isNotEmpty()) out["variables"] = variables.map { it.toYamlValue() }
        out["steps"] = steps.map { it.yaml }
        out.putAll(extra)
    }
}

data class AzurePipelineStep(
    val kind: String,
    val yaml: Map<String, Any?>,
    val displayName: String? = yaml["displayName"]?.toString(),
    val producesArtifacts: List<PipelineArtifactRef> = emptyList(),
    val consumesArtifacts: List<PipelineArtifactRef> = emptyList(),
    val deploysStacks: List<InfrastructureStackRef> = emptyList(),
)

data class Trigger(
    val batch: Boolean?,
    val branches: BranchFilter,
    val paths: BranchFilter,
) {
    fun toYamlValue(): Map<String, Any?> = linkedMapOf<String, Any?>().also { out ->
        batch?.let { out["batch"] = it }
        branches.toYamlValue()?.let { out["branches"] = it }
        paths.toYamlValue()?.let { out["paths"] = it }
    }
}

data class BranchFilter(
    val include: List<String>,
    val exclude: List<String>,
) {
    fun toYamlValue(): Map<String, Any?>? {
        if (include.isEmpty() && exclude.isEmpty()) return null
        return linkedMapOf(
            "include" to include,
            "exclude" to exclude,
        ).filterValues { (it as List<*>).isNotEmpty() }
    }
}

private fun dependencyIds(value: Any?, referenceType: Class<*>): List<String> = when (value) {
    is PipelineStage -> if (referenceType == PipelineStage::class.java) listOf(value.id) else emptyList()
    is PipelineJob -> if (referenceType == PipelineJob::class.java) listOf(value.id) else emptyList()
    is String -> listOf(value)
    is Iterable<*> -> value.flatMap { dependencyIds(it, referenceType) }
    else -> emptyList()
}
