package org.kikd.azure

import org.kikd.core.CloudDefinition
import org.kikd.core.KikdGraphBuilder
import org.kikd.core.KikdGraphContributor
import org.kikd.core.KikdGraphNodeKind
import org.kikd.core.KikdDsl
import org.kikd.core.KikdProjectPlanner
import org.kikd.core.KikdProjectBuilder
import org.kikd.core.cloud

class AzureCloud internal constructor(
    val components: List<AzureComponent>,
) : CloudDefinition, KikdGraphContributor {
    override val name: String = "azure"

    override fun contributeGraph(builder: KikdGraphBuilder) {
        builder.node(
            id = AZURE_GRAPH_ID,
            kind = KikdGraphNodeKind.CLOUD,
            label = "Azure",
            parentId = KikdProjectPlanner.ROOT_ID,
            payload = this,
        )
        components.filterIsInstance<AzureGraphContributor>().forEach {
            it.contributeAzureGraph(builder, AZURE_GRAPH_ID)
        }
    }
}

interface AzureComponent

interface AzureGraphContributor {
    fun contributeAzureGraph(builder: KikdGraphBuilder, cloudNodeId: String)
}

const val AZURE_GRAPH_ID: String = "azure"

@KikdDsl
class AzureBuilder {
    private val components = mutableListOf<AzureComponent>()

    fun register(component: AzureComponent) {
        components.removeAll { it::class == component::class }
        components += component
    }

    fun build(): AzureCloud = AzureCloud(components.toList())
}

fun KikdProjectBuilder.azure(block: AzureBuilder.() -> Unit) {
    addCloud(AzureBuilder().apply(block).build())
}

inline fun <reified T : AzureComponent> AzureCloud.component(): T? =
    components.filterIsInstance<T>().singleOrNull()

fun org.kikd.core.KikdProject.azure(): AzureCloud? = cloud()
