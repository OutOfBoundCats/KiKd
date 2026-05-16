# kikd-examples

`kikd-examples` is a runnable consumer of the KiKd DSL. Each example lives in its own Kotlin file and has its own `main` method.

## Run

```bash
./gradlew :kikd-examples:runSimpleInfrastructureExample
./gradlew :kikd-examples:runNestedNetworkExample
./gradlew :kikd-examples:runStageVariablesExample
./gradlew :kikd-examples:runVariableGroupExample
./gradlew :kikd-examples:runStageOutputVariablesExample
```

The default `:kikd-examples:run` task runs `SimpleInfrastructureExample`.

## Generated Examples

```text
kikd-examples/output/
  01-simple-infra/
  02-nested-network/
  03-stage-variables/
  04-variable-group/
  05-stage-output-variables/
```

- `01-simple-infra` creates one resource group with Bicep.
- `02-nested-network` adds a virtual network with nested subnet objects.
- `03-stage-variables` declares Azure Pipeline stage variables and passes them into infrastructure parameters for deployment.
- `04-variable-group` reads deployment values from an Azure Pipeline variable group and passes them into infrastructure parameters.
- `05-stage-output-variables` emits Azure Pipeline output variables from a shell step, maps them into Kotlin stage variable handles, and passes them into infrastructure parameters.

## Source Files

- `SimpleInfrastructureExample.kt`
- `NestedNetworkExample.kt`
- `StageVariablesExample.kt`
- `VariableGroupExample.kt`
- `StageOutputVariablesExample.kt`

## Notable Snippets

Nested virtual network properties:

```kotlin
virtualNetwork("vnet-kikd-network", resourceGroup = rg) {
    addressPrefixes.clear()
    addressPrefixes += "10.0.0.0/16"
    subnet("Subnet-1", "10.0.0.0/24")
    subnet("Subnet-2", "10.0.1.0/24")
}
```

Pipeline variable group:

```kotlin
pipeline {
    val sharedInfra = variableGroup("shared-infra")
    val environment = sharedInfra.variable("ENVIRONMENT")
}
```

Stage variables also return handles:

```kotlin
stage("Deploy") {
    val environment = variable("environment", "dev")
    val namePrefix = variable("namePrefix", "kikd")

    job("DeployInfra") {
        deployAzd(
            stackName = "main",
            artifact = infraArtifact,
            params = mapOf(
                "environment" to environment.macroReference(),
                "namePrefix" to namePrefix.macroReference(),
            ),
        )
    }
}
```

Stage output variables can be declared once, attached to a named raw shell step, and mapped back into Kotlin handles before deployment:

```kotlin
val environmentOutput = stageOutputVariable("environment")

val build = stage("Build") {
    job("ResolveInfra") {
        bash(
            "echo \"##vso[task.setvariable variable=resolvedEnvironment;isOutput=true]dev\"",
            stageOutput(environmentOutput, "resolvedEnvironment"),
        ) {
            raw("name", "setInfraValues")
        }
    }
}

stage("Deploy") {
    dependsOn(build)
    val environment = variable(environmentOutput)

    job("DeployInfra") {
        deployAzd(
            stackName = "main",
            artifact = infraArtifact,
            params = mapOf("environment" to environment.macroReference()),
        )
    }
}
```

Infrastructure parameters are emitted into `main.bicepparam` as `readEnvironmentVariable(...)`, so the pipeline can supply values through step environment variables when it runs `azd provision`.
