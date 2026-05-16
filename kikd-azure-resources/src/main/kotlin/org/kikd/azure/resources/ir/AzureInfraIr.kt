package org.kikd.azure.resources.ir

import org.kikd.azure.resources.api.AzureResource
import org.kikd.azure.resources.api.AzureResourceReference
import org.kikd.azure.resources.api.AzureStack
import org.kikd.azure.resources.api.BicepExpression
import org.kikd.azure.resources.api.InfraExpression
import org.kikd.azure.resources.api.NameExpressionAware
import org.kikd.azure.resources.api.TerraformExpression
import org.kikd.azure.resources.builtins.AzureResourceGroup
import org.kikd.azure.resources.builtins.AzureResourceGroupReference
import org.kikd.azure.resources.render.azureSymbol
import org.kikd.azure.resources.render.escapeBicep

interface AzureInfraResource : AzureResource {
    fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec? = null
    fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec? = null
}

data class AzureBicepResourceSpec(
    val symbol: String,
    val type: AzureBicepResourceType,
    val attributes: List<IacAttribute>,
)

data class AzureTerraformResourceSpec(
    val logicalName: String,
    val type: AzureTerraformResourceType,
    val attributes: List<IacAttribute>,
    val requiresClientConfig: Boolean = false,
)

@JvmInline
value class AzureBicepResourceType(val value: String) {
    init {
        require(value.contains("@")) { "Bicep resource type must include an API version: $value" }
    }
}

@JvmInline
value class AzureTerraformResourceType(val value: String) {
    init {
        require(value.startsWith("azurerm_")) { "Terraform AzureRM resource type must start with azurerm_: $value" }
    }
}

data class AzureResourceType(
    val bicep: AzureBicepResourceType,
    val terraform: AzureTerraformResourceType,
)

fun azureResourceType(bicep: String, terraform: String): AzureResourceType =
    AzureResourceType(AzureBicepResourceType(bicep), AzureTerraformResourceType(terraform))

object AzureResourceTypes {
    val AppServicePlan: AzureResourceType =
        azureResourceType("Microsoft.Web/serverfarms@2023-12-01", "azurerm_service_plan")
    val KeyVault: AzureResourceType =
        azureResourceType("Microsoft.KeyVault/vaults@2023-07-01", "azurerm_key_vault")
    val ResourceGroup: AzureResourceType =
        azureResourceType("Microsoft.Resources/resourceGroups@2022-09-01", "azurerm_resource_group")
    val StorageAccount: AzureResourceType =
        azureResourceType("Microsoft.Storage/storageAccounts@2023-01-01", "azurerm_storage_account")
    val VirtualNetwork: AzureResourceType =
        azureResourceType("Microsoft.Network/virtualNetworks@2025-01-01", "azurerm_virtual_network")
    val WebApp: AzureResourceType =
        azureResourceType("Microsoft.Web/sites@2023-12-01", "azurerm_linux_web_app")
}

data class IacAttribute(
    val name: String,
    val value: IacValue,
)

sealed interface IacValue {
    data object Null : IacValue
    data class StringLiteral(val value: String) : IacValue
    data class NumberLiteral(val value: Number) : IacValue
    data class BooleanLiteral(val value: Boolean) : IacValue
    data class Expression(val expression: String) : IacValue
    data class ObjectLiteral(val attributes: List<IacAttribute>) : IacValue
    data class ArrayLiteral(val values: List<IacValue>) : IacValue
}

class AzureBicepIrContext(
    val stack: AzureStack,
    val allResources: List<AzureResource>,
    private val locationParameter: String,
) {
    fun location(value: String): IacValue =
        if (value == stack.location) iacExpression(locationParameter) else iacString(value)

    fun resourceGroupScope(resourceGroup: AzureResourceGroupReference): IacValue =
        if (allResources.any { it is AzureResourceGroup && it.logicalName == resourceGroup.logicalName }) {
            iacExpression("resourceGroup(${azureSymbol(resourceGroup.logicalName)}.name)")
        } else {
            iacExpression("resourceGroup('${resourceGroup.name.replace("'", "''")}')")
        }

    fun resourceId(resource: AzureResource): IacValue =
        iacExpression("${azureSymbol(resource.logicalName)}.id")

    fun nameValue(resource: AzureResource): IacValue {
        val expression = extractNameExpression(resource)
        if (expression != null) {
            return iacExpression(renderBicepInterpolation(expression.template))
        }
        return iacString(resource.name)
    }

    fun scopedResourceAttributes(
        resource: AzureResourceReference,
        resourceGroup: AzureResourceGroupReference,
        location: String,
        tags: Map<String, String>,
    ): List<IacAttribute> {
        val nameValue = if (resource is AzureResource) {
            nameValue(resource)
        } else {
            iacString(resource.name)
        }
        return iacAttributes {
            required("name", nameValue)
            required("scope", resourceGroupScope(resourceGroup))
            required("location", location(location))
            optional("tags", tags)
        }
    }
}

class AzureTerraformIrContext(
    val stack: AzureStack,
    val allResources: List<AzureResource>,
) {
    fun resourceAttributes(
        resource: AzureResource,
        resourceGroup: AzureResourceGroupReference,
        location: String,
        tags: Map<String, String>,
    ): List<IacAttribute> =
        iacAttributes {
            required("name", nameValue(resource))
            required("resource_group_name", resourceGroupName(resourceGroup))
            required("location", location)
            optional("tags", tags)
        }

    fun resourceGroupName(resourceGroup: AzureResourceGroupReference): IacValue =
        if (allResources.any { it is AzureResourceGroup && it.logicalName == resourceGroup.logicalName }) {
            iacExpression("azurerm_resource_group.${azureSymbol(resourceGroup.logicalName)}.name")
        } else {
            iacString(resourceGroup.name)
        }

    fun tenantId(expression: String): IacValue =
        when (expression) {
            "tenant().tenantId", "subscription().tenantId" ->
                iacExpression("data.azurerm_client_config.current.tenant_id")
            else -> iacExpression(expression)
        }

    fun resourceId(terraformType: AzureTerraformResourceType, resource: AzureResource): IacValue =
        iacExpression("${terraformType.value}.${azureSymbol(resource.logicalName)}.id")

    fun nameValue(resource: AzureResource): IacValue {
        val expression = extractNameExpression(resource)
        if (expression != null) {
            return iacString(renderTerraformInterpolation(expression.template))
        }
        return iacString(resource.name)
    }
}

private fun extractNameExpression(resource: AzureResource): InfraExpression? {
    return (resource as? NameExpressionAware)?.nameExpression
}

private fun renderBicepInterpolation(template: String): String {
    val parts = mutableListOf<String>()
    var lastEnd = 0
    val regex = Regex("\\$\\{([^}]+)}")
    regex.findAll(template).forEach { match ->
        if (match.range.first > lastEnd) {
            parts.add("'${template.substring(lastEnd, match.range.first).escapeBicep()}'")
        }
        parts.add(match.groupValues[1])
        lastEnd = match.range.last + 1
    }
    if (lastEnd < template.length) {
        parts.add("'${template.substring(lastEnd).escapeBicep()}'")
    }
    return parts.joinToString(" + ")
}

private fun renderTerraformInterpolation(template: String): String {
    return template.replace(Regex("\\$\\{([^}]+)}")) { match ->
        "\${var.${match.groupValues[1]}}"
    }
}

fun iacAttribute(name: String, value: Any?): IacAttribute =
    IacAttribute(name, iacValue(value))

fun iacAttributes(vararg attributes: IacAttribute): List<IacAttribute> =
    attributes.toList()

fun iacAttributes(block: IacObjectBuilder.() -> Unit): List<IacAttribute> =
    IacObjectBuilder().apply(block).build().attributes

fun iacString(value: String): IacValue = IacValue.StringLiteral(value)

fun iacExpression(expression: String): IacValue = IacValue.Expression(expression)

fun iacObject(vararg attributes: IacAttribute): IacValue.ObjectLiteral =
    IacValue.ObjectLiteral(attributes.toList())

fun iacObject(block: IacObjectBuilder.() -> Unit): IacValue.ObjectLiteral =
    IacObjectBuilder().apply(block).build()

fun iacArray(values: Iterable<Any?>): IacValue =
    IacValue.ArrayLiteral(values.map(::iacValue))

fun iacArray(vararg values: Any?): IacValue =
    IacValue.ArrayLiteral(values.map(::iacValue))

fun iacValue(value: Any?): IacValue = when (value) {
    null -> IacValue.Null
    is IacValue -> value
    is InfraExpression -> iacExpression(value.template)
    is BicepExpression -> iacExpression(value.expression)
    is TerraformExpression -> iacExpression(value.expression)
    is String -> iacString(value)
    is Number -> IacValue.NumberLiteral(value)
    is Boolean -> IacValue.BooleanLiteral(value)
    is Map<*, *> -> IacValue.ObjectLiteral(
        value.entries.map { (key, entryValue) ->
            IacAttribute(key.toString(), iacValue(entryValue))
        },
    )
    is Iterable<*> -> iacArray(value)
    is Array<*> -> iacArray(value.asList())
    else -> iacString(value.toString())
}

class IacObjectBuilder internal constructor() {
    private val attributes = mutableListOf<IacAttribute>()

    fun required(name: String, value: Any?) {
        require(value != null) { "Required IaC property '$name' cannot be null." }
        attributes += iacAttribute(name, value)
    }

    fun optional(name: String, value: Any?) {
        if (value != null) attributes += iacAttribute(name, value)
    }

    fun optional(name: String, values: Collection<*>) {
        if (values.isNotEmpty()) attributes += iacAttribute(name, values)
    }

    fun optional(name: String, values: Map<*, *>) {
        if (values.isNotEmpty()) attributes += iacAttribute(name, values)
    }

    fun build(): IacValue.ObjectLiteral = IacValue.ObjectLiteral(attributes.toList())
}
