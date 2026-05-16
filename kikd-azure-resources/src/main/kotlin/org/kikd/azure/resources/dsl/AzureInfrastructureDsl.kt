package org.kikd.azure.resources.dsl

import org.kikd.azure.AzureBuilder
import org.kikd.azure.resources.api.AzureInfrastructure
import org.kikd.azure.resources.api.AzureInfrastructureModel
import org.kikd.azure.resources.api.AzureResource
import org.kikd.azure.resources.api.AzureStack
import org.kikd.azure.resources.api.InfraExpression
import org.kikd.azure.resources.api.InfraParameter
import org.kikd.azure.resources.api.ResourceName
import org.kikd.azure.resources.builtins.AppServicePlanBuilder
import org.kikd.azure.resources.builtins.AzureAppServicePlan
import org.kikd.azure.resources.builtins.AzureKeyVault
import org.kikd.azure.resources.builtins.AzureResourceGroup
import org.kikd.azure.resources.builtins.AzureResourceGroupReference
import org.kikd.azure.resources.builtins.AzureStorageAccount
import org.kikd.azure.resources.builtins.AzureVirtualNetwork
import org.kikd.azure.resources.builtins.AzureWebApp
import org.kikd.azure.resources.builtins.ExistingAzureResourceGroup
import org.kikd.azure.resources.builtins.KeyVaultBuilder
import org.kikd.azure.resources.builtins.StorageAccountBuilder
import org.kikd.azure.resources.builtins.VirtualNetworkBuilder
import org.kikd.azure.resources.builtins.WebAppBuilder
import org.kikd.core.KikdDsl

fun infraExpr(template: String): ResourceName = ResourceName.Expression(InfraExpression(template))

fun AzureBuilder.infrastructure(block: AzureInfrastructureBuilder.() -> Unit) {
    val builder = AzureInfrastructureBuilder()
    builder.block()
    register(AzureInfrastructure(builder.build()))
}

@KikdDsl
class AzureInfrastructureBuilder {
    private val stacks = mutableListOf<AzureStack>()

    fun stack(
        name: String = "main",
        location: String = "eastus",
        block: AzureStackBuilder.() -> Unit,
    ) {
        stacks += AzureStackBuilder(name, location).apply(block).build()
    }

    fun build(): AzureInfrastructureModel = AzureInfrastructureModel(stacks.toList())
}

@KikdDsl
class AzureStackBuilder internal constructor(
    private val name: String,
    val location: String,
) {
    private val resources = mutableListOf<AzureResource>()
    private val parameters = mutableListOf<InfraParameter>()

    fun parameter(name: String, type: String = "string", defaultValue: Any? = null): InfraParameter =
        InfraParameter(name, type, defaultValue).also { parameters += it }

    fun <T : AzureResource> resource(resource: T): T {
        require(resource.logicalName.isNotBlank()) { "Resource logical name cannot be blank." }
        require(resources.none { it.logicalName == resource.logicalName }) {
            "Resource '${resource.logicalName}' is already defined in stack '$name'."
        }
        resources += resource
        return resource
    }

    fun resourceGroup(
        name: String,
        location: String = this.location,
        tags: Map<String, String> = emptyMap(),
    ): AzureResourceGroup = resourceGroup(name.staticName(), location, tags)

    fun resourceGroup(
        name: ResourceName,
        location: String = this.location,
        tags: Map<String, String> = emptyMap(),
    ): AzureResourceGroup {
        val resolvedName = resolveName(name)
        return resource(
            AzureResourceGroup(
                logicalName = resolvedName,
                name = resolvedName,
                location = location,
                tags = tags,
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun virtualNetwork(
        name: String,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: VirtualNetworkBuilder.() -> Unit = {},
    ): AzureVirtualNetwork = virtualNetwork(name.staticName(), resourceGroup, location, block)

    fun virtualNetwork(
        name: ResourceName,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: VirtualNetworkBuilder.() -> Unit = {},
    ): AzureVirtualNetwork {
        val options = VirtualNetworkBuilder().apply(block)
        val resolvedName = resolveName(name)
        return resource(
            AzureVirtualNetwork(
                logicalName = resolvedName,
                name = resolvedName,
                resourceGroup = resourceGroup,
                location = location,
                addressPrefixes = options.addressPrefixes.toList(),
                subnets = options.subnets.toList(),
                enableDdosProtection = options.enableDdosProtection,
                enableVmProtection = options.enableVmProtection,
                tags = options.tags.toMap(),
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun storageAccount(
        name: String,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: StorageAccountBuilder.() -> Unit = {},
    ): AzureStorageAccount = storageAccount(name.staticName(), resourceGroup, location, block)

    fun storageAccount(
        name: ResourceName,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: StorageAccountBuilder.() -> Unit = {},
    ): AzureStorageAccount {
        val options = StorageAccountBuilder().apply(block)
        val resolvedName = resolveName(name)
        return resource(
            AzureStorageAccount(
                logicalName = resolvedName,
                name = resolvedName,
                resourceGroup = resourceGroup,
                location = location,
                sku = options.sku,
                kind = options.kind,
                accessTier = options.accessTier,
                tags = options.tags.toMap(),
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun appServicePlan(
        name: String,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: AppServicePlanBuilder.() -> Unit = {},
    ): AzureAppServicePlan = appServicePlan(name.staticName(), resourceGroup, location, block)

    fun appServicePlan(
        name: ResourceName,
        resourceGroup: AzureResourceGroupReference,
        location: String = this.location,
        block: AppServicePlanBuilder.() -> Unit = {},
    ): AzureAppServicePlan {
        val options = AppServicePlanBuilder().apply(block)
        val resolvedName = resolveName(name)
        return resource(
            AzureAppServicePlan(
                logicalName = resolvedName,
                name = resolvedName,
                resourceGroup = resourceGroup,
                location = location,
                skuName = options.skuName,
                osType = options.osType,
                tags = options.tags.toMap(),
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun webApp(
        name: String,
        resourceGroup: AzureResourceGroupReference,
        servicePlan: AzureAppServicePlan,
        location: String = this.location,
        block: WebAppBuilder.() -> Unit = {},
    ): AzureWebApp = webApp(name.staticName(), resourceGroup, servicePlan, location, block)

    fun webApp(
        name: ResourceName,
        resourceGroup: AzureResourceGroupReference,
        servicePlan: AzureAppServicePlan,
        location: String = this.location,
        block: WebAppBuilder.() -> Unit = {},
    ): AzureWebApp {
        val options = WebAppBuilder().apply(block)
        val resolvedName = resolveName(name)
        return resource(
            AzureWebApp(
                logicalName = resolvedName,
                name = resolvedName,
                resourceGroup = resourceGroup,
                servicePlan = servicePlan,
                location = location,
                appSettings = options.appSettings.toMap(),
                tags = options.tags.toMap(),
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun keyVault(
        name: String,
        resourceGroup: AzureResourceGroupReference,
        tenantIdExpression: String,
        location: String = this.location,
        block: KeyVaultBuilder.() -> Unit = {},
    ): AzureKeyVault = keyVault(name.staticName(), resourceGroup, tenantIdExpression, location, block)

    fun keyVault(
        name: ResourceName,
        resourceGroup: AzureResourceGroupReference,
        tenantIdExpression: String,
        location: String = this.location,
        block: KeyVaultBuilder.() -> Unit = {},
    ): AzureKeyVault {
        val options = KeyVaultBuilder().apply(block)
        val resolvedName = resolveName(name)
        return resource(
            AzureKeyVault(
                logicalName = resolvedName,
                name = resolvedName,
                resourceGroup = resourceGroup,
                tenantIdExpression = tenantIdExpression,
                location = location,
                skuName = options.skuName,
                tags = options.tags.toMap(),
                nameExpression = nameExpressionOf(name),
            ),
        )
    }

    fun resourceGroupRef(name: String): ExistingAzureResourceGroup =
        ExistingAzureResourceGroup(logicalName = name, name = name)

    fun build(): AzureStack = AzureStack(
        name = name,
        location = location,
        resources = resources.toList(),
        parameters = parameters.toList(),
    )

    private fun resolveName(name: ResourceName): String = when (name) {
        is ResourceName.Static -> name.value
        is ResourceName.Expression -> {
            val template = name.template.template
            template.replace(Regex("\\$\\{([^}]+)}")) { "_${it.groupValues[1]}_" }
                .replace(Regex("[^a-zA-Z0-9_-]"), "-")
                .trim('-')
                .ifBlank { "unnamed" }
        }
    }

    private fun nameExpressionOf(name: ResourceName): InfraExpression? = when (name) {
        is ResourceName.Static -> null
        is ResourceName.Expression -> name.template
    }

    private fun String.staticName(): ResourceName = ResourceName.Static(this)
}
