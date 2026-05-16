package org.kikd.azure.resources.api

import org.kikd.azure.AzureCloud
import org.kikd.azure.AzureComponent
import org.kikd.azure.AzureGraphContributor
import org.kikd.azure.component
import org.kikd.core.KikdDependencyKind
import org.kikd.core.KikdGraphBuilder
import org.kikd.core.KikdGraphNodeKind
import org.kikd.core.graphIdSegment

data class AzureInfrastructure(
    val model: AzureInfrastructureModel,
) : AzureComponent, AzureGraphContributor {
    override fun contributeAzureGraph(builder: KikdGraphBuilder, cloudNodeId: String) {
        val infrastructureId = "$cloudNodeId:infrastructure"
        builder.node(
            id = infrastructureId,
            kind = KikdGraphNodeKind.INFRASTRUCTURE,
            label = "Azure Infrastructure",
            parentId = cloudNodeId,
            payload = model,
        )

        model.stacks.forEach { stack ->
            val stackId = "$infrastructureId:stack:${graphIdSegment(stack.name)}"
            builder.node(
                id = stackId,
                kind = KikdGraphNodeKind.STACK,
                label = stack.name,
                parentId = infrastructureId,
                payload = stack,
            )
            stack.resources.forEach { resource ->
                builder.node(
                    id = resourceNodeId(stackId, resource.logicalName),
                    kind = KikdGraphNodeKind.RESOURCE,
                    label = resource.logicalName,
                    parentId = stackId,
                    payload = resource,
                )
            }
            val resourceIds = stack.resources.associateBy { it.logicalName }
            stack.resources.forEach { resource ->
                resource.dependsOn.forEach { dependency ->
                    if (resourceIds.containsKey(dependency.logicalName)) {
                        builder.dependsOn(
                            dependentId = resourceNodeId(stackId, resource.logicalName),
                            dependencyId = resourceNodeId(stackId, dependency.logicalName),
                            kind = KikdDependencyKind.RESOURCE_DEPENDS_ON,
                        )
                    }
                }
            }
        }
    }

    private fun resourceNodeId(stackId: String, logicalName: String): String =
        "$stackId:resource:${graphIdSegment(logicalName)}"
}

data class AzureInfrastructureModel(
    val stacks: List<AzureStack>,
)

data class InfraParameter(
    val name: String,
    val type: String = "string",
    val defaultValue: Any? = null,
) {
    fun templateReference(): String = "\${$name}"
}

data class AzureStack(
    val name: String,
    val location: String,
    val resources: List<AzureResource>,
    val parameters: List<InfraParameter> = emptyList(),
)

fun AzureCloud.infrastructure(): AzureInfrastructure? = component()
