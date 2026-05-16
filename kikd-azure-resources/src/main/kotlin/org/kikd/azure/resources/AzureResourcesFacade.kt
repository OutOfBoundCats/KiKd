package org.kikd.azure.resources

import org.kikd.azure.AzureBuilder
import org.kikd.azure.resources.api.AzureInfrastructure
import org.kikd.azure.resources.dsl.AzureInfrastructureBuilder

typealias AzureResourceReference = org.kikd.azure.resources.api.AzureResourceReference
typealias AzureResource = org.kikd.azure.resources.api.AzureResource
typealias BicepExpression = org.kikd.azure.resources.api.BicepExpression
typealias TerraformExpression = org.kikd.azure.resources.api.TerraformExpression
typealias InfraExpression = org.kikd.azure.resources.api.InfraExpression
typealias ResourceName = org.kikd.azure.resources.api.ResourceName
typealias InfraParameter = org.kikd.azure.resources.api.InfraParameter
typealias AzureInfrastructure = org.kikd.azure.resources.api.AzureInfrastructure
typealias AzureInfrastructureModel = org.kikd.azure.resources.api.AzureInfrastructureModel
typealias AzureStack = org.kikd.azure.resources.api.AzureStack

typealias AzureResourceGroupReference = org.kikd.azure.resources.builtins.AzureResourceGroupReference
typealias ExistingAzureResourceGroup = org.kikd.azure.resources.builtins.ExistingAzureResourceGroup
typealias AzureResourceGroup = org.kikd.azure.resources.builtins.AzureResourceGroup
typealias AzureVirtualNetwork = org.kikd.azure.resources.builtins.AzureVirtualNetwork
typealias AzureStorageAccount = org.kikd.azure.resources.builtins.AzureStorageAccount
typealias AzureAppServicePlan = org.kikd.azure.resources.builtins.AzureAppServicePlan
typealias AzureWebApp = org.kikd.azure.resources.builtins.AzureWebApp
typealias AzureKeyVault = org.kikd.azure.resources.builtins.AzureKeyVault

typealias AzureBicepBackend = org.kikd.azure.resources.backend.AzureBicepBackend
typealias AzureTerraformBackend = org.kikd.azure.resources.backend.AzureTerraformBackend
typealias AzdConfigBackend = org.kikd.azure.resources.backend.AzdConfigBackend
typealias AzureInfraResource = org.kikd.azure.resources.ir.AzureInfraResource
typealias AzureBicepIrContext = org.kikd.azure.resources.ir.AzureBicepIrContext
typealias AzureTerraformIrContext = org.kikd.azure.resources.ir.AzureTerraformIrContext
typealias AzureBicepResourceSpec = org.kikd.azure.resources.ir.AzureBicepResourceSpec
typealias AzureTerraformResourceSpec = org.kikd.azure.resources.ir.AzureTerraformResourceSpec
typealias AzureResourceType = org.kikd.azure.resources.ir.AzureResourceType
typealias AzureBicepResourceType = org.kikd.azure.resources.ir.AzureBicepResourceType
typealias AzureTerraformResourceType = org.kikd.azure.resources.ir.AzureTerraformResourceType
typealias IacAttribute = org.kikd.azure.resources.ir.IacAttribute
typealias IacValue = org.kikd.azure.resources.ir.IacValue
typealias IacObjectBuilder = org.kikd.azure.resources.ir.IacObjectBuilder

val AzureResourceTypes: org.kikd.azure.resources.ir.AzureResourceTypes
    get() = org.kikd.azure.resources.ir.AzureResourceTypes

fun azureResourceType(bicep: String, terraform: String): AzureResourceType =
    org.kikd.azure.resources.ir.azureResourceType(bicep, terraform)

fun bicepExpression(expression: String): BicepExpression =
    org.kikd.azure.resources.api.bicepExpression(expression)

fun terraformExpression(expression: String): TerraformExpression =
    org.kikd.azure.resources.api.terraformExpression(expression)

fun iacAttribute(name: String, value: Any?): IacAttribute =
    org.kikd.azure.resources.ir.iacAttribute(name, value)

fun iacAttributes(block: IacObjectBuilder.() -> Unit): List<IacAttribute> =
    org.kikd.azure.resources.ir.iacAttributes(block)

fun iacExpression(expression: String): IacValue =
    org.kikd.azure.resources.ir.iacExpression(expression)

fun iacObject(vararg attributes: IacAttribute): IacValue =
    org.kikd.azure.resources.ir.iacObject(*attributes)

fun iacObject(block: IacObjectBuilder.() -> Unit): IacValue =
    org.kikd.azure.resources.ir.iacObject(block)

fun iacArray(vararg values: Any?): IacValue =
    org.kikd.azure.resources.ir.iacArray(*values)

fun infraExpr(template: String): ResourceName =
    org.kikd.azure.resources.dsl.infraExpr(template)

fun AzureBuilder.infrastructure(block: AzureInfrastructureBuilder.() -> Unit) {
    val builder = AzureInfrastructureBuilder()
    builder.block()
    register(AzureInfrastructure(builder.build()))
}
