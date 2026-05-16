# kikd-core

`kikd-core` contains the shared primitives used by every cloud and renderer module.

## Responsibilities

- Defines the top-level `kikdProject { ... }` DSL entrypoint.
- Defines `CloudDefinition`, which lets cloud modules attach their own model to a project.
- Defines the generation API, `GeneratorBackend`, and plan-aware backend contracts.
- Defines the shared dependency graph planner used before backend rendering.
- Defines `GeneratedFile`, the common output unit returned by every backend.

This module does not know about Azure, Bicep, Terraform, Azure DevOps, or any resource model.

## Main API

```kotlin
val project = kikdProject {
    // Cloud modules add extension functions here.
}
```

Generate into the default `output/` folder:

```kotlin
generate(project, backend1, backend2)
```

Generate into an explicit folder:

```kotlin
generate(
    project = project,
    outputDir = Path.of("custom-output"),
    backend1,
    backend2,
)
```

Build the shared plan directly:

```kotlin
val plan = KikdProjectPlanner.plan(project)
val graph = plan.graph
```

The graph contains hierarchy nodes and dependency edges contributed by cloud modules. Generation builds this plan once and passes it to plan-aware backends.

## Generated Files

Backends return paths relative to the chosen output root:

```kotlin
GeneratedFile(
    relativePath = "infra/main.bicep",
    content = "..."
)
```

`generate(...)` validates that generated paths are relative and cannot escape the output directory.

## Boundary

`kikd-core` should stay cloud-neutral. Resource models, renderer contexts, and backend-specific capabilities belong in cloud modules such as `kikd-azure-resources`.
