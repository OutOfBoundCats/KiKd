package org.kikd.azure.resources.ir

import org.kikd.azure.resources.api.AzureStack
import org.kikd.azure.resources.builtins.AzureSubnet
import org.kikd.azure.resources.builtins.AzureVirtualNetwork
import org.kikd.azure.resources.builtins.ExistingAzureResourceGroup
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AzureIrCompilerTest {
    private val exampleWidgetType = azureResourceType("Microsoft.Example/widgets@2024-01-01", "azurerm_example_widget")

    @Test
    fun `bicep compiler renders escaped strings objects arrays and expressions`() {
        val output = AzureBicepIrCompiler().renderResource(
            AzureBicepResourceSpec(
                symbol = "demo-widget",
                type = exampleWidgetType.bicep,
                attributes = listOf(
                    iacAttribute("name", "demo's widget"),
                    iacAttribute("location", iacExpression("location")),
                    iacAttribute(
                        "properties",
                        iacObject(
                            iacAttribute("enabled", true),
                            iacAttribute("items", iacArray("one", iacExpression("two()"))),
                        ),
                    ),
                ),
            ),
        )

        assertContains(output, "resource demo_widget 'Microsoft.Example/widgets@2024-01-01' = {")
        assertContains(output, "name: 'demo''s widget'")
        assertContains(output, "location: location")
        assertContains(output, "enabled: true")
        assertContains(output, "two()")
    }

    @Test
    fun `iac object builder includes required values and skips absent optional values`() {
        val output = AzureBicepIrCompiler().renderResource(
            AzureBicepResourceSpec(
                symbol = "demo-widget",
                type = exampleWidgetType.bicep,
                attributes = listOf(
                    iacAttribute(
                        "properties",
                        iacObject {
                            required("enabled", true)
                            optional("notes", null)
                            optional("items", emptyList<String>())
                        },
                    ),
                ),
            ),
        )

        assertContains(output, "enabled: true")
        assertFalse(output.contains("notes:"))
        assertFalse(output.contains("items:"))
    }

    @Test
    fun `terraform compiler renders escaped strings maps arrays expressions and labels`() {
        val output = AzureTerraformIrCompiler().renderResource(
            AzureTerraformResourceSpec(
                logicalName = "demo-widget",
                type = exampleWidgetType.terraform,
                attributes = listOf(
                    iacAttribute("name", "demo \"widget\""),
                    iacAttribute("location", iacExpression("var.location")),
                    iacAttribute("tags", mapOf("cost-center" to "shared")),
                    iacAttribute("values", listOf("one", "two")),
                ),
            ),
        )

        assertContains(output, "resource \"azurerm_example_widget\" \"demo_widget\" {")
        assertContains(output, "name = \"demo \\\"widget\\\"\"")
        assertContains(output, "location = var.location")
        assertContains(output, "\"cost-center\" = \"shared\"")
        assertContains(output, "values = [\"one\", \"two\"]")
    }

    @Test
    fun `bicep parameter values are escaped`() {
        assertEquals("'east''us'", AzureBicepIrCompiler().renderParameterValue("east'us"))
    }

    @Test
    fun `bicep scoped resource attributes render resource group scope`() {
        val resourceGroup = ExistingAzureResourceGroup("rg-demo", "rg-demo")
        val context = AzureBicepIrContext(
            stack = AzureStack("main", "eastus", emptyList()),
            allResources = emptyList(),
            locationParameter = "location",
        )
        val output = AzureBicepIrCompiler().renderResource(
            AzureBicepResourceSpec(
                symbol = "demo",
                type = exampleWidgetType.bicep,
                attributes = context.scopedResourceAttributes(resourceGroup, resourceGroup, "eastus", emptyMap()),
            ),
        )

        assertContains(output, "scope: resourceGroup('rg-demo')")
        assertContains(output, "location: location")
    }

    @Test
    fun `virtual network bicep renders nested subnet objects`() {
        val resourceGroup = ExistingAzureResourceGroup("rg-demo", "rg-demo")
        val virtualNetwork = AzureVirtualNetwork(
            logicalName = "vnet-demo",
            name = "vnet-demo",
            resourceGroup = resourceGroup,
            location = "eastus",
            addressPrefixes = listOf("10.0.0.0/16"),
            subnets = listOf(
                AzureSubnet("Subnet-1", "10.0.0.0/24"),
                AzureSubnet("Subnet-2", "10.0.1.0/24"),
            ),
            enableDdosProtection = true,
        )
        val context = AzureBicepIrContext(
            stack = AzureStack("main", "eastus", listOf(virtualNetwork)),
            allResources = listOf(virtualNetwork),
            locationParameter = "location",
        )

        val output = AzureBicepIrCompiler().renderResource(virtualNetwork.bicepSpec(context))

        assertContains(output, "subnets: [")
        assertContains(output, "name: 'Subnet-1'")
        assertContains(output, "addressPrefix: '10.0.0.0/24'")
        assertContains(output, "name: 'Subnet-2'")
        assertContains(output, "addressPrefix: '10.0.1.0/24'")
        assertContains(output, "enableDdosProtection: true")
        assertContains(output, "Microsoft.Network/virtualNetworks@2025-01-01")
    }

    @Test
    fun `virtual network requires at least one address prefix`() {
        val resourceGroup = ExistingAzureResourceGroup("rg-demo", "rg-demo")

        assertFailsWith<IllegalArgumentException> {
            AzureVirtualNetwork(
                logicalName = "vnet-demo",
                name = "vnet-demo",
                resourceGroup = resourceGroup,
                location = "eastus",
                addressPrefixes = emptyList(),
            )
        }
    }
}
