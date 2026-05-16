package org.kikd.azure.resources.backend

import org.kikd.azure.azure
import org.kikd.azure.resources.api.infrastructure
import org.kikd.core.GeneratedFile
import org.kikd.core.KikdProjectPlan
import org.kikd.core.PlanAwareGeneratorBackend

class AzdConfigBackend(
    private val projectName: String = "kikd-project",
    private val infraDirectory: String = "infra",
) : PlanAwareGeneratorBackend {
    override val id: String = "azd-config"

    override fun generate(plan: KikdProjectPlan): List<GeneratedFile> {
        val infrastructure = plan.project.azure()?.infrastructure()?.model ?: return emptyList()
        if (infrastructure.stacks.isEmpty()) return emptyList()

        return listOf(
            GeneratedFile("azure.yaml", renderAzureYaml()),
        )
    }

    private fun renderAzureYaml(): String = buildString {
        appendLine("name: $projectName")
        appendLine("infra:")
        appendLine("  provider: bicep")
        appendLine("  path: $infraDirectory")
    }.trimEnd()
}
