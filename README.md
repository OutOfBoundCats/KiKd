# KiKd

KiKd is a Kotlin DSL for defining Azure DevOps pipelines and Azure infrastructure from code, then generating structured YAML and infrastructure-as-code output.

The current design builds a shared dependency graph before rendering. Pipeline stages/jobs/steps, artifacts, infrastructure stacks, and Azure resources are represented as graph nodes; backends then lower that plan into Azure Pipelines YAML, Bicep, or Terraform through dedicated compilers.

## What It Generates

By default, generation writes to `output/`:

```text
output/
  .azure-pipeline/
    pipelines/
      azure-pipelines.yml
    stages/
      build.yml
      deploy.yml
    steps/
      build-buildapp.yml
      deploy-deployapp.yml
  infra/
    main.bicep
    main.bicepparam
```

If `AzureTerraformBackend()` is used instead of `AzureBicepBackend()`, Terraform files are written under `output/infra/`:

```text
output/
  infra/
    provider.tf
    data.tf
    resource-group-rg-kikd-demo.tf
    virtual-network-vnet-kikd-demo.tf
    storage-account-stkikddemo.tf
```

## Module Structure

- [`kikd-core`](kikd-core/README.md): shared project DSL and generation API.
- [`kikd-azure`](kikd-azure/README.md): Azure cloud shell and component registration.
- [`kikd-azure-pipelines`](kikd-azure-pipelines/README.md): Azure DevOps pipeline DSL and YAML/template renderer.
- [`kikd-azure-resources`](kikd-azure-resources/README.md): Azure resource DSL, type-safe resource handles, Azure IaC IR, and Bicep/Terraform compiler backends.
- [`kikd-azure-integration`](kikd-azure-integration/): bridge between pipelines and resources (e.g., passing pipeline parameters into infrastructure stacks).
- [`kikd-examples`](kikd-examples/README.md): runnable sample project.
- [`kikd-test-project`](kikd-test-project/README.md): consumer-style integration tests.

## Source Layout

The repo is modularized at both the Gradle and package level:

- `kikd-core` owns only cloud-neutral project composition and file generation contracts.
- `kikd-azure` owns Azure component registration without depending on pipeline or resource implementations.
- `kikd-azure-pipelines` separates pipeline model types, DSL builders, and YAML backend rendering.
- `kikd-azure-resources` separates public resource contracts, expression wrappers, infrastructure models, resource DSL builders, built-in resources, IR compilers, and Bicep/Terraform backends.
- `kikd-azure-integration` bridges `kikd-azure-pipelines` and `kikd-azure-resources`, providing convenience helpers like passing pipeline parameters into infrastructure stacks.
- `kikd-examples` and `kikd-test-project` consume the published module APIs instead of reaching into implementation details.

Public convenience imports are exposed from `org.kikd.azure.resources`, while implementation-specific resource packages remain available for advanced users.

## Tech Stack

- **Kotlin** 2.3.20 on the JVM
- **Gradle** with Kotlin DSL and version catalogs
- **kotlinx-datetime**, **kotlinx-serialization**, **kotlinx-coroutines** from the KotlinX ecosystem
- **JUnit Platform** for testing
- Build convention plugins shared via `buildSrc`

## Quick Example

```kotlin
import org.kikd.azure.azure
import org.kikd.azure.pipelines.AzurePipelinesYamlBackend
import org.kikd.azure.pipelines.pipeline
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infrastructure
import org.kikd.core.generate
import org.kikd.core.kikdProject

val project = kikdProject {
    azure {
        pipeline {
            val infraArtifact = artifact("infra", path = "output/infra")

            val build = stage("Build") {
                job("BuildApp") {
                    pool("ubuntu-latest")
                    checkout()
                    script("./gradlew check")
                    publishPipelineArtifact(infraArtifact)
                }
            }

            stage("Deploy") {
                dependsOn(build)
                job("DeployApp") {
                    pool("ubuntu-latest")
                    downloadPipelineArtifact(infraArtifact)
                    deployBicep(stackName = "main", artifact = infraArtifact)
                }
            }
        }

        infrastructure {
            stack(name = "main", location = "eastus") {
                val rg = resourceGroup("rg-kikd-demo")
                val plan = appServicePlan("plan-kikd-demo", resourceGroup = rg)

                virtualNetwork("vnet-kikd-demo", resourceGroup = rg)
                storageAccount("stkikddemo", resourceGroup = rg)
                webApp("app-kikd-demo", resourceGroup = rg, servicePlan = plan)
            }
        }
    }
}

generate(
    project,
    AzurePipelinesYamlBackend(),
    AzureBicepBackend(),
)
```

## Type-Safe References

Pipeline declarations return handles:

```kotlin
val build = stage("Build") { ... }

stage("Deploy") {
    dependsOn(build)
}
```

Pipeline artifacts can also be modeled explicitly so the planner can connect producer and consumer steps:

```kotlin
val infraArtifact = artifact("infra", path = "output/infra")

job("BuildApp") {
    publishPipelineArtifact(infraArtifact)
}

job("DeployApp") {
    downloadPipelineArtifact(infraArtifact)
    deployBicep(stackName = "main", artifact = infraArtifact)
}
```

Azure resource declarations return handles:

```kotlin
val rg = resourceGroup("rg-kikd-demo")
val plan = appServicePlan("plan-kikd-demo", resourceGroup = rg)

storageAccount("stkikddemo", resourceGroup = rg)
webApp("app-kikd-demo", resourceGroup = rg, servicePlan = plan)
```

## User-Defined Azure Resources

Users can add Azure resources not covered by the library by implementing `AzureResource` and `AzureInfraResource`, then returning Bicep and/or Terraform IR specs.

```kotlin
class AzureContainerRegistry(
    override val logicalName: String,
    override val name: String,
    val resourceGroup: AzureResourceGroupReference,
    val location: String,
) : AzureResource, AzureInfraResource {
    override val dependsOn = listOf(resourceGroup)
    private val resourceType = azureResourceType(
        bicep = "Microsoft.ContainerRegistry/registries@2023-07-01",
        terraform = "azurerm_container_registry",
    )

    override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
        AzureBicepResourceSpec(
            symbol = logicalName,
            type = resourceType.bicep,
            attributes = context.scopedResourceAttributes(this, resourceGroup, location, emptyMap()) + listOf(
                iacAttribute("sku", iacObject(iacAttribute("name", "Basic"))),
                iacAttribute("properties", iacObject(iacAttribute("adminUserEnabled", false))),
            ),
        )

    override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
        AzureTerraformResourceSpec(
            logicalName = logicalName,
            type = resourceType.terraform,
            attributes = listOf(
                iacAttribute("name", name),
                iacAttribute("resource_group_name", context.resourceGroupName(resourceGroup)),
                iacAttribute("location", location),
                iacAttribute("sku", "Basic"),
                iacAttribute("admin_enabled", false),
            ),
        )
}
```

Register it in a stack:

```kotlin
val rg = resourceGroup("rg-demo")
resource(AzureContainerRegistry("acrdemo", "acrdemo", rg, location))
```

If a resource provides only Terraform IR and the user generates with `AzureBicepBackend`, generation fails with a clear error. The reverse is also true.

## Generation API

Default output directory:

```kotlin
generate(project, AzurePipelinesYamlBackend(), AzureBicepBackend())
```

Explicit output directory:

```kotlin
generate(
    project = project,
    outputDir = Path.of("custom-output"),
    AzurePipelinesYamlBackend(),
    AzureTerraformBackend(),
)
```

Generation creates parent directories and overwrites targeted files. It does not clean stale files from previous runs.

## Commands

```bash
./gradlew check
./gradlew :kikd-examples:run
find output -type f | sort
```

## Current Boundaries

- Azure Pipelines output is split into real template include files.
- Bicep emits `main.bicep` and `main.bicepparam`.
- Terraform emits provider/data files and one file per Terraform-capable resource.
- The project does not run Azure CLI, Bicep validation, Terraform validation, or Azure DevOps validation yet.

## Contributing

Contributions are welcome. By contributing to this repository, you agree that your contributions may be incorporated into both the AGPLv3 and commercial versions of KiKd. You retain copyright to your contributions but grant the project maintainers the right to dual-license them. See [`LICENSE.md`](LICENSE.md) for details.

## License

Copyright © 2026 BitMask LLP. KiKd is dual-licensed:

- **AGPLv3** — for personal, educational, and open-source use. Full text: https://www.gnu.org/licenses/agpl-3.0.en.html
- **Commercial License** — for proprietary or enterprise use that does not comply with AGPLv3 obligations. Contact **raj.patil@bitmask.in** for commercial licensing.

See [`LICENSE.md`](LICENSE.md) for the full licensing terms and FAQ.
