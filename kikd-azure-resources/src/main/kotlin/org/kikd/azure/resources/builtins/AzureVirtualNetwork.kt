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
import org.kikd.azure.resources.ir.iacAttribute
import org.kikd.azure.resources.ir.iacAttributes
import org.kikd.azure.resources.ir.iacObject
import org.kikd.core.KikdDsl

data class AzureVirtualNetwork(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val location: String,
    val addressPrefixes: List<String>,
    val subnets: List<AzureSubnet> = emptyList(),
    val enableDdosProtection: Boolean = false,
    val enableVmProtection: Boolean = false,
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureInfraResource, NameExpressionAware {
    init {
        require(addressPrefixes.isNotEmpty()) { "Virtual network '$logicalName' requires at least one address prefix." }
    }

    override val dependsOn: List<AzureResourceReference> = listOf(resourceGroup)

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.VirtualNetwork.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, tags) + iacAttribute(
                "properties",
                iacObject {
                    required(
                        "addressSpace",
                        iacObject {
                            required("addressPrefixes", addressPrefixes)
                        },
                    )
                    optional(
                        "subnets",
                        subnets.map { subnet ->
                            subnet.toBicepValue()
                        },
                    )
                    if (enableDdosProtection) required("enableDdosProtection", true)
                    if (enableVmProtection) required("enableVmProtection", true)
                },
            ),
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.VirtualNetwork.terraform,
            attributes = context.resourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required("address_space", addressPrefixes)
            },
        )
}

data class AzureSubnet(
    val name: String,
    val addressPrefix: String,
) {
    fun toBicepValue() = iacObject {
        required("name", name)
        required(
            "properties",
            iacObject {
                required("addressPrefix", addressPrefix)
            },
        )
    }
}

@KikdDsl
class VirtualNetworkBuilder {
    val addressPrefixes: MutableList<String> = mutableListOf("10.0.0.0/16")
    val subnets: MutableList<AzureSubnet> = mutableListOf()
    var enableDdosProtection: Boolean = false
    var enableVmProtection: Boolean = false
    val tags: MutableMap<String, String> = linkedMapOf()

    @Deprecated("Use addressPrefixes instead.", ReplaceWith("addressPrefixes"))
    val addressSpaces: MutableList<String>
        get() = addressPrefixes

    fun subnet(name: String, addressPrefix: String) {
        subnets += AzureSubnet(name, addressPrefix)
    }
}
