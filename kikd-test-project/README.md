# kikd-test-project

`kikd-test-project` contains consumer-style integration tests for KiKd.

## Purpose

The tests use the public DSL the same way a downstream project would. This catches regressions in:

- public imports
- DSL shape
- type-safe handles
- generated output paths
- renderer output content

## Current Coverage

The integration tests verify:

- Azure DevOps root pipeline, stage templates, and step templates are generated.
- Stage dependencies declared with `PipelineStage` handles render as `dependsOn`.
- Bicep generation emits `infra/main.bicep` and `infra/main.bicepparam`.
- Terraform generation emits `provider.tf`, optional `data.tf`, and one resource file per modeled resource.
- Azure virtual network, storage account, and other typed resources are rendered.

## Run

Run only this module:

```bash
./gradlew :kikd-test-project:test
```

Run the whole workspace:

```bash
./gradlew check
```

## Test Style

Tests generate into temporary directories with `Files.createTempDirectory(...)`. They assert expected files exist and check representative content instead of comparing entire golden files.

This keeps tests focused on behavior while allowing renderer formatting to evolve.
