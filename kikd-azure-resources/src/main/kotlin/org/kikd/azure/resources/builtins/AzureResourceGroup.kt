package org.kikd.azure.resources.builtins

import org.kikd.azure.resources.api.AzureResource
import org.kikd.azure.resources.api.AzureResourceReference
import org.kikd.azure.resources.api.InfraExpression
import org.kikd.azure.resources.api.NameExpressionAware
import org.kikd.azure.resources.ir.AzureBicepIrContext
import org.kikd.azure.resources.ir.AzureBicepResourceSpec
import org.kikd.azure.resources.ir.AzureInfraResource
import org.kikd.azure.resources.ir.AzureResourceTypes
import org.kikd.azure.resources.ir.AzureTerraformIrContext
import org.kikd.azure.resources.ir.AzureTerraformResourceSpec
import org.kikd.azure.resources.ir.iacAttributes

interface AzureResourceGroupReference : AzureResourceReference

data class ExistingAzureResourceGroup(
    override val logicalName: String,
    override val name: String,
) : AzureResourceGroupReference

data class AzureResourceGroup(
    override val logicalName: String,
    override val name: String,
    val location: String,
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureResourceGroupReference, AzureInfraResource, NameExpressionAware {
    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.ResourceGroup.bicep,
            attributes = iacAttributes {
                required("name", context.nameValue(this@AzureResourceGroup))
                required("location", context.location(location))
                optional("tags", tags)
            },
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.ResourceGroup.terraform,
            attributes = iacAttributes {
                required("name", context.nameValue(this@AzureResourceGroup))
                required("location", location)
                optional("tags", tags)
            },
        )
}
