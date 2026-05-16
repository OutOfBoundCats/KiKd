# kikd-azure-resources

`kikd-azure-resources` contains the Azure infrastructure DSL, built-in Azure resources, Azure IaC intermediate representation (IR), and Bicep/Terraform compiler backends.

## Responsibilities

- Adds `infrastructure { ... }` under `azure { ... }`.
- Provides type-safe resource handles.
- Defines `AzureResource` and `AzureInfraResource`.
- Defines Bicep and Terraform IR specs plus compilers.
- Provides `AzureBicepBackend` and `AzureTerraformBackend`.

## Source Layout

```text
src/main/kotlin/org/kikd/azure/resources/
  AzureResourcesFacade.kt
  api/
    AzureResourceModel.kt
  backend/
    AzureBicepBackend.kt
    AzureTerraformBackend.kt
  builtins/
    AzureAppServicePlan.kt
    AzureKeyVault.kt
    AzureResourceGroup.kt
    AzureStorageAccount.kt
    AzureVirtualNetwork.kt
    AzureWebApp.kt
  dsl/
    AzureInfrastructureDsl.kt
  ir/
    AzureInfraIr.kt
    AzureBicepIrCompiler.kt
    AzureTerraformIrCompiler.kt
  render/
    AzureRenderHelpers.kt
```

Layer responsibilities:

- `api`: stable resource contracts and infrastructure model.
- `dsl`: stack builder and registration DSL.
- `builtins`: one file per built-in Azure resource.
- `ir`: backend-neutral values, backend-specific resource specs, and compilers.
- `render`: shared formatting helpers.
- `backend`: thin Bicep/Terraform output orchestrators.
- root facade: typealiases and the ergonomic `infrastructure { ... }` import.

## Built-In Resources

- Resource group
- Virtual network
- Storage account
- App Service plan
- Linux web app
- Key Vault

## Usage

```kotlin
azure {
    infrastructure {
        stack(name = "main", location = "eastus") {
            val rg = resourceGroup("rg-kikd-demo")
            val plan = appServicePlan("plan-kikd-demo", resourceGroup = rg)

            virtualNetwork("vnet-kikd-demo", resourceGroup = rg) {
                addressPrefixes.clear()
                addressPrefixes += "10.0.0.0/16"
                subnet("Subnet-1", "10.0.0.0/24")
                subnet("Subnet-2", "10.0.1.0/24")
            }
            storageAccount("stkikddemo", resourceGroup = rg)
            webApp("app-kikd-demo", resourceGroup = rg, servicePlan = plan)
        }
    }
}
```

Generate Bicep:

```kotlin
generate(project, AzureBicepBackend())
```

Generate Terraform:

```kotlin
generate(project, AzureTerraformBackend())
```

## User-Defined Resources

Users can define Azure resources that KiKd does not ship yet:

```kotlin
import org.kikd.azure.resources.AzureBicepIrContext
import org.kikd.azure.resources.AzureBicepResourceSpec
import org.kikd.azure.resources.AzureInfraResource
import org.kikd.azure.resources.AzureResource
import org.kikd.azure.resources.iacAttribute

class MyAzureResource(
    override val logicalName: String,
    override val name: String,
) : AzureResource, AzureInfraResource {
    private val resourceType = azureResourceType(
        bicep = "Microsoft.Example/widgets@2024-01-01",
        terraform = "azurerm_example_widget",
    )

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = resourceType.bicep,
            attributes = listOf(iacAttribute("name", name)),
        )
}
```

Register the resource:

```kotlin
resource(MyAzureResource("widget-demo", "widget-demo"))
```

If the user selects `AzureTerraformBackend()` for this resource, generation fails because the resource does not provide Terraform IR.

To support both IaC backends, return both Bicep and Terraform specs:

```kotlin
class MyAzureResource(...) : AzureResource, AzureInfraResource {
    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec = ...
    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec = ...
}
```

## Backend Capability Checks

`AzureBicepBackend` requires every resource in the selected infrastructure model to implement `AzureInfraResource` and provide a Bicep IR spec.

`AzureTerraformBackend` requires every resource in the selected infrastructure model to implement `AzureInfraResource` and provide a Terraform IR spec.

This catches backend/resource mismatches during generation instead of silently producing incomplete output.

## Required And Optional Fields

Shared IR objects can be built with explicit required and optional fields:

```kotlin
iacObject {
    required("addressSpace", iacObject {
        required("addressPrefixes", listOf("10.0.0.0/16"))
    })
    optional("subnets", emptyList<Any>())
}
```

All built-in Bicep resources now use that shared path. `virtualNetwork { ... }` defaults `addressPrefixes` to `10.0.0.0/16`, requires at least one address prefix before generation, and leaves `enableDdosProtection` and `enableVmProtection` at their documented default of `false` unless callers opt in.
