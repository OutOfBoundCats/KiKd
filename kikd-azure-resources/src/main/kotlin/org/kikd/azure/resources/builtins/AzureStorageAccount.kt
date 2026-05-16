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

data class AzureStorageAccount(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val location: String,
    val sku: String,
    val kind: String,
    val accessTier: String,
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureInfraResource, NameExpressionAware {
    override val dependsOn: List<AzureResourceReference> = listOf(resourceGroup)

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.StorageAccount.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required(
                    "sku",
                    iacObject {
                        required("name", sku)
                    },
                )
                required("kind", kind)
                required(
                    "properties",
                    iacObject {
                        required("accessTier", accessTier)
                    },
                )
            },
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec {
        val skuParts = sku.split("_", limit = 2)
        return AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.StorageAccount.terraform,
            attributes = context.resourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required("account_tier", skuParts.getOrElse(0) { "Standard" })
                required("account_replication_type", skuParts.getOrElse(1) { "LRS" })
                required("account_kind", kind)
                required("access_tier", accessTier)
            },
        )
    }
}

@KikdDsl
class StorageAccountBuilder {
    var sku: String = "Standard_LRS"
    var kind: String = "StorageV2"
    var accessTier: String = "Hot"
    val tags: MutableMap<String, String> = linkedMapOf()
}
