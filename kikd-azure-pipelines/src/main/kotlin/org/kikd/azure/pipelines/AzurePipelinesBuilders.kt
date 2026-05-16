package org.kikd.azure.pipelines

import org.kikd.core.KikdDsl

@KikdDsl
class AzurePipelineBuilder {
    var name: String? = null
    private var trigger: Trigger? = null
    private val parameters = mutableListOf<PipelineParameter>()
    private val variables = mutableListOf<PipelineVariableEntry>()
    private val resources = linkedMapOf<String, Any?>()
    private val stages = mutableListOf<AzurePipelineStage>()
    private val extra = linkedMapOf<String, Any?>()

    fun trigger(block: TriggerBuilder.() -> Unit) {
        trigger = TriggerBuilder().apply(block).build()
    }

    fun parameter(
        name: String,
        type: String = "string",
        default: Any? = null,
        values: List<Any?> = emptyList(),
    ): PipelineParameter =
        PipelineParameter(name, type, default, values).also { parameters += it }

    fun variable(
        name: String,
        value: Any?,
        isSecret: Boolean = false,
    ): PipelineVariableReference {
        variables += PipelineVariable(name, value, isSecret)
        return PipelineVariableReference(name)
    }

    fun variableGroup(name: String): PipelineVariableGroup =
        PipelineVariableGroup(name).also { variables += it }

    fun artifact(name: String, path: String): PipelineArtifactRef =
        PipelineArtifactRef(name, path)

    fun stageOutputVariable(name: String): PipelineStageOutputVariable =
        PipelineStageOutputVariable(name)

    fun stageOutput(
        variable: PipelineStageOutputVariable,
        outputName: String,
    ): PipelineStageOutputBinding = PipelineStageOutputBinding(variable, outputName)

    fun resources(block: GenericMapBuilder.() -> Unit) {
        resources.putAll(GenericMapBuilder().apply(block).values)
    }

    fun stage(id: String, block: StageBuilder.() -> Unit): PipelineStage {
        stages += StageBuilder(id).apply(block).build()
        return PipelineStage(id)
    }

    fun raw(key: String, value: Any?) {
        extra[key] = value
    }

    fun build(): AzurePipeline = AzurePipeline(
        name = name,
        trigger = trigger,
        parameters = parameters.toList(),
        variables = variables.toList(),
        resources = resources.toMap(),
        stages = stages.toList(),
        extra = extra.toMap(),
    )
}

@KikdDsl
class TriggerBuilder {
    var batch: Boolean? = null
    private val branches = FilterBuilder()
    private val paths = FilterBuilder()

    fun branches(block: FilterBuilder.() -> Unit) {
        branches.block()
    }

    fun paths(block: FilterBuilder.() -> Unit) {
        paths.block()
    }

    fun build(): Trigger = Trigger(batch, branches.build(), paths.build())
}

@KikdDsl
class FilterBuilder {
    private val include = mutableListOf<String>()
    private val exclude = mutableListOf<String>()

    fun include(vararg values: String) {
        include += values
    }

    fun exclude(vararg values: String) {
        exclude += values
    }

    fun build(): BranchFilter = BranchFilter(include.toList(), exclude.toList())
}

@KikdDsl
class StageBuilder internal constructor(
    private val id: String,
) {
    var displayName: String? = null
    var condition: String? = null
    var dependsOn: Any? = null
    private val typedDependencies = mutableListOf<PipelineStage>()
    private val variables = mutableListOf<PipelineVariableEntry>()
    private val jobs = mutableListOf<AzurePipelineJob>()
    private val extra = linkedMapOf<String, Any?>()

    fun dependsOn(vararg stages: PipelineStage) {
        typedDependencies += stages
    }

    fun variable(name: String, value: Any?): PipelineVariableReference {
        variables += PipelineVariable(name, value)
        return PipelineVariableReference(name)
    }

    fun variable(outputVariable: PipelineStageOutputVariable): PipelineVariableReference =
        variable(outputVariable.name, outputVariable.stageDependencyExpression())

    fun stageOutput(
        variable: PipelineStageOutputVariable,
        outputName: String,
    ): PipelineStageOutputBinding = PipelineStageOutputBinding(variable, outputName)

    fun variableGroup(name: String): PipelineVariableGroup =
        PipelineVariableGroup(name).also { variables += it }

    fun job(id: String, block: JobBuilder.() -> Unit): PipelineJob {
        jobs += JobBuilder(stageId = this.id, id = id).apply(block).build()
        return PipelineJob(id)
    }

    fun raw(key: String, value: Any?) {
        extra[key] = value
    }

    fun build(): AzurePipelineStage =
        AzurePipelineStage(
            id = id,
            displayName = displayName,
            condition = condition,
            dependsOn = resolvedDependsOn(),
            variables = variables.toList(),
            jobs = jobs.toList(),
            extra = extra.toMap(),
        )

    private fun resolvedDependsOn(): Any? =
        resolveDependsOn(dependsOn, typedDependencies) { it.id }
}

@KikdDsl
class JobBuilder internal constructor(
    private val stageId: String,
    private val id: String,
) {
    var displayName: String? = null
    var condition: String? = null
    var dependsOn: Any? = null
    private val typedDependencies = mutableListOf<PipelineJob>()
    private var pool: Any? = null
    private val variables = mutableListOf<PipelineVariableEntry>()
    private val steps = mutableListOf<AzurePipelineStep>()
    private val extra = linkedMapOf<String, Any?>()

    fun pool(vmImage: String) {
        pool = linkedMapOf("vmImage" to vmImage)
    }

    fun dependsOn(vararg jobs: PipelineJob) {
        typedDependencies += jobs
    }

    fun variable(name: String, value: Any?): PipelineVariableReference {
        variables += PipelineVariable(name, value)
        return PipelineVariableReference(name)
    }

    fun variable(outputVariable: PipelineStageOutputVariable): PipelineVariableReference =
        variable(outputVariable.name, outputVariable.stageDependencyExpression())

    fun stageOutput(
        variable: PipelineStageOutputVariable,
        outputName: String,
    ): PipelineStageOutputBinding = PipelineStageOutputBinding(variable, outputName)

    fun variableGroup(name: String): PipelineVariableGroup =
        PipelineVariableGroup(name).also { variables += it }

    fun checkout(repository: String = "self") {
        steps += AzurePipelineStep(
            kind = "checkout",
            yaml = linkedMapOf("checkout" to repository),
        )
    }

    fun script(script: String, block: StepBuilder.() -> Unit = {}) {
        steps += StepBuilder("script", script, stageId, id).apply(block).build()
    }

    fun bash(
        script: String,
        vararg outputVariables: PipelineStageOutputBinding,
        block: StepBuilder.() -> Unit = {},
    ) {
        steps += StepBuilder(
            key = "bash",
            script = script,
            stageId = stageId,
            jobId = id,
            stageOutputVariables = outputVariables.toList(),
        ).apply(block).build()
    }

    fun pwsh(script: String, block: StepBuilder.() -> Unit = {}) {
        steps += StepBuilder("pwsh", script, stageId, id).apply(block).build()
    }

    fun task(task: String, block: TaskBuilder.() -> Unit = {}) {
        steps += TaskBuilder(task).apply(block).build()
    }

    fun publishPipelineArtifact(artifact: PipelineArtifactRef, block: TaskBuilder.() -> Unit = {}) {
        steps += TaskBuilder("PublishPipelineArtifact@1").apply {
            displayName = "Publish ${artifact.name}"
            inputs["targetPath"] = artifact.path
            inputs["artifact"] = artifact.name
            block()
        }.build(
            producesArtifacts = listOf(artifact),
        )
    }

    fun downloadPipelineArtifact(artifact: PipelineArtifactRef, block: TaskBuilder.() -> Unit = {}) {
        steps += TaskBuilder("DownloadPipelineArtifact@2").apply {
            displayName = "Download ${artifact.name}"
            inputs["buildType"] = "current"
            inputs["artifactName"] = artifact.name
            inputs["targetPath"] = artifact.path
            block()
        }.build(
            consumesArtifacts = listOf(artifact),
        )
    }

    fun deployBicep(
        stackName: String = "main",
        artifact: PipelineArtifactRef,
        location: String = "eastus",
        block: StepBuilder.() -> Unit = {},
    ) {
        steps += StepBuilder(
            key = "script",
            script = "az deployment sub create --location $location --template-file ${artifact.path}/main.bicep --parameters ${artifact.path}/main.bicepparam",
            stageId = stageId,
            jobId = id,
        ).apply {
            displayName = "Deploy $stackName infrastructure"
            block()
        }.build(
            consumesArtifacts = listOf(artifact),
            deploysStacks = listOf(InfrastructureStackRef(stackName)),
        )
    }

    fun deployAzd(
        stackName: String = "main",
        params: Map<String, String> = emptyMap(),
        artifact: PipelineArtifactRef? = null,
        block: StepBuilder.() -> Unit = {},
    ) {
        steps += StepBuilder(
            key = "bash",
            script = "azd provision",
            stageId = stageId,
            jobId = id,
        ).apply {
            displayName = "Deploy $stackName infrastructure with azd"
            env(params.mapKeys { (key, _) -> key.uppercase() })
            block()
        }.build(
            consumesArtifacts = artifact?.let { listOf(it) } ?: emptyList(),
            deploysStacks = listOf(InfrastructureStackRef(stackName)),
        )
    }

    fun rawStep(value: Map<String, Any?>) {
        steps += AzurePipelineStep(kind = "raw", yaml = value)
    }

    fun raw(key: String, value: Any?) {
        extra[key] = value
    }

    fun build(): AzurePipelineJob =
        AzurePipelineJob(
            id = id,
            displayName = displayName,
            condition = condition,
            dependsOn = resolvedDependsOn(),
            pool = pool,
            variables = variables.toList(),
            steps = steps.toList(),
            extra = extra.toMap(),
        )

    private fun resolvedDependsOn(): Any? =
        resolveDependsOn(dependsOn, typedDependencies) { it.id }
}

@KikdDsl
class StepBuilder internal constructor(
    private val key: String,
    private val script: String,
    private val stageId: String,
    private val jobId: String,
    private val stageOutputVariables: List<PipelineStageOutputBinding> = emptyList(),
) {
    var displayName: String? = null
    var condition: String? = null
    private val env = linkedMapOf<String, String>()
    private val extra = linkedMapOf<String, Any?>()

    fun env(name: String, value: String) {
        env[name] = value
    }

    fun env(values: Map<String, String>) {
        env.putAll(values)
    }

    fun raw(key: String, value: Any?) {
        extra[key] = value
    }

    fun build(
        consumesArtifacts: List<PipelineArtifactRef> = emptyList(),
        deploysStacks: List<InfrastructureStackRef> = emptyList(),
    ): AzurePipelineStep {
        val stepName = extra["name"]?.toString()
        if (stageOutputVariables.isNotEmpty()) {
            require(!stepName.isNullOrBlank()) {
                "Steps that produce stage output variables must define a non-blank name."
            }
            stageOutputVariables.forEach { binding ->
                binding.variable.assignFrom(stageId, jobId, stepName, binding.outputName)
            }
        }

        return AzurePipelineStep(
            kind = key,
            yaml = linkedMapOf<String, Any?>(
                key to script,
            ).also { out ->
                displayName?.let { out["displayName"] = it }
                condition?.let { out["condition"] = it }
                if (env.isNotEmpty()) out["env"] = env.toMap()
                out.putAll(extra)
            },
            consumesArtifacts = consumesArtifacts,
            deploysStacks = deploysStacks,
        )
    }
}

@KikdDsl
class TaskBuilder internal constructor(
    private val task: String,
) {
    var displayName: String? = null
    val inputs: MutableMap<String, Any?> = linkedMapOf()
    private val extra = linkedMapOf<String, Any?>()

    fun raw(key: String, value: Any?) {
        extra[key] = value
    }

    fun build(
        producesArtifacts: List<PipelineArtifactRef> = emptyList(),
        consumesArtifacts: List<PipelineArtifactRef> = emptyList(),
    ): AzurePipelineStep =
        AzurePipelineStep(
            kind = task,
            yaml = linkedMapOf<String, Any?>(
                "task" to task,
            ).also { out ->
                displayName?.let { out["displayName"] = it }
                if (inputs.isNotEmpty()) out["inputs"] = inputs
                out.putAll(extra)
            },
            producesArtifacts = producesArtifacts,
            consumesArtifacts = consumesArtifacts,
        )
}

@KikdDsl
class GenericMapBuilder {
    val values: MutableMap<String, Any?> = linkedMapOf()

    fun value(key: String, value: Any?) {
        values[key] = value
    }
}

private fun <T> List<T>.singleOrList(): Any =
    singleOrNull() ?: this

private inline fun <reified T> resolveDependsOn(
    explicitValue: Any?,
    typedDependencies: List<T>,
    idOf: (T) -> String,
): Any? = when {
    typedDependencies.isNotEmpty() -> typedDependencies.map(idOf).singleOrList()
    explicitValue is T -> idOf(explicitValue)
    explicitValue is Iterable<*> -> explicitValue.map { dependency ->
        if (dependency is T) idOf(dependency) else dependency
    }.singleOrList()
    else -> explicitValue
}
