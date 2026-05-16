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

data class AzureWebApp(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val servicePlan: AzureAppServicePlan,
    val location: String,
    val appSettings: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    override val nameExpression: InfraExpression? = null,
) : AzureResource, AzureInfraResource, NameExpressionAware {
    override val dependsOn: List<AzureResourceReference> = listOf(resourceGroup, servicePlan)

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = AzureResourceTypes.WebApp.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, tags) + iacAttribute(
                "properties",
                iacObject {
                    required("serverFarmId", context.resourceId(servicePlan))
                    required(
                        "siteConfig",
                        iacObject {
                            required(
                                "appSettings",
                                appSettings.map { (key, value) ->
                                    iacObject {
                                        required("name", key)
                                        required("value", value)
                                    }
                                },
                            )
                        },
                    )
                },
            ),
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = AzureResourceTypes.WebApp.terraform,
            attributes = context.resourceAttributes(this, resourceGroup, location, tags) + iacAttributes {
                required("service_plan_id", context.resourceId(AzureResourceTypes.AppServicePlan.terraform, servicePlan))
                optional("app_settings", appSettings)
            },
        )
}

@KikdDsl
class WebAppBuilder {
    val appSettings: MutableMap<String, String> = linkedMapOf()
    val tags: MutableMap<String, String> = linkedMapOf()
}
