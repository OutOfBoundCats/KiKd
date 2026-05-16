# kikd-azure

`kikd-azure` is the Azure cloud shell for KiKd.

## Responsibilities

- Adds the `azure { ... }` DSL extension to `kikdProject { ... }`.
- Defines `AzureCloud`, `AzureBuilder`, and `AzureComponent`.
- Provides component registration and lookup helpers used by Azure-focused modules.

This module intentionally does not define Azure resources, Azure DevOps pipelines, Bicep, or Terraform. Those capabilities live in separate modules that attach components to `AzureCloud`.

## Usage

```kotlin
val project = kikdProject {
    azure {
        // Azure modules add DSL extensions here.
    }
}
```

For example:

```kotlin
azure {
    pipeline { ... }
    infrastructure { ... }
}
```

`pipeline { ... }` is provided by `kikd-azure-pipelines`.

`infrastructure { ... }`, Azure resources, `AzureBicepBackend`, and `AzureTerraformBackend` are provided by `kikd-azure-resources`.

## Extension Pattern

Azure feature modules should:

1. Define a component type implementing `AzureComponent`.
2. Add an extension function on `AzureBuilder`.
3. Register the component with `register(...)`.
4. Add lookup helpers on `AzureCloud` when backends need to read that component.

This keeps the Azure root module small and lets capabilities evolve independently.
