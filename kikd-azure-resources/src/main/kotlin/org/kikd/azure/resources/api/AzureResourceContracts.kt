package org.kikd.azure.resources.api

interface AzureResourceReference {
    val logicalName: String
    val name: String
}

interface AzureResource : AzureResourceReference {
    val dependsOn: List<AzureResourceReference>
        get() = emptyList()
}

interface NameExpressionAware {
    val nameExpression: InfraExpression?
}

@JvmInline
value class InfraExpression(val template: String)

sealed class ResourceName {
    data class Static(val value: String) : ResourceName()
    data class Expression(val template: InfraExpression) : ResourceName()
}
