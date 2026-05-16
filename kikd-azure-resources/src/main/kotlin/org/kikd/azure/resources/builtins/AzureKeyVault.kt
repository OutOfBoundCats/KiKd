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
import org.kikd.azure.resources.ir.iacArray
import org.kikd.azure.resources.ir.iacAttribute
import org.kikd.azure.resources.ir.iacAttributes
import org.kikd.azure.resources.ir.iacExpression
import org.kikd.azure.resources.ir.iacObject
import org.kikd.core.KikdDsl

data class AzureKeyVault(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val tenantIdExpression: String,
    val location: String,
    val skuName: String,
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureInfraResource, NameExpressionAware {
    override val dependsOn: List<AzureResourceReference> = listOf(resourceGroup)

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.KeyVault.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, tags) + iacAttribute(
                "properties",
                iacObject {
                    required("tenantId", iacExpression(tenantIdExpression))
                    required(
                        "sku",
                        iacObject {
                            required("family", "A")
                            required("name", skuName)
                        },
                    )
                    required("accessPolicies", iacArray())
                },
            ),
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.KeyVault.terraform,
            requiresClientConfig = true,
            attributes = context.resourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required("tenant_id", context.tenantId(tenantIdExpression))
                required("sku_name", skuName)
            },
        )
}

@KikdDsl
class KeyVaultBuilder {
    var skuName: String = "standard"
    val tags: MutableMap<String, String> = linkedMapOf()
}
