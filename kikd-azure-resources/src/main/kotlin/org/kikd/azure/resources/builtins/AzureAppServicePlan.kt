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
import org.kikd.azure.resources.ir.iacObject
import org.kikd.core.KikdDsl

data class AzureAppServicePlan(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val location: String,
    val skuName: String,
    val osType: String,
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureInfraResource, NameExpressionAware {
    override val dependsOn: List<AzureResourceReference> = listOf(resourceGroup)

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.AppServicePlan.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required(
                    "sku",
                    iacObject {
                        required("name", skuName)
                    },
                )
                required("kind", osType.lowercase())
                required(
                    "properties",
                    iacObject {
                        required("reserved", osType.equals("Linux", ignoreCase = true))
                    },
                )
            },
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.AppServicePlan.terraform,
            attributes = context.resourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required("os_type", osType)
                required("sku_name", skuName)
            },
        )
}

@KikdDsl
class AppServicePlanBuilder {
    var skuName: String = "B1"
    var osType: String = "Linux"
    val tags: MutableMap<String, String> = linkedMapOf()
}
