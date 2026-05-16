package org.kikd.testproject

import org.kikd.azure.azure
import org.kikd.azure.pipelines.AzurePipelinesYamlBackend
import org.kikd.azure.pipelines.pipeline
import org.kikd.azure.resources.AzdConfigBackend
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.AzureBicepIrContext
import org.kikd.azure.resources.AzureBicepResourceSpec
import org.kikd.azure.resources.AzureInfraResource
import org.kikd.azure.resources.AzureResource
import org.kikd.azure.resources.AzureResourceReference
import org.kikd.azure.resources.AzureTerraformBackend
import org.kikd.azure.resources.AzureTerraformIrContext
import org.kikd.azure.resources.AzureTerraformResourceSpec
import org.kikd.azure.resources.AzureResourceTypes
import org.kikd.azure.resources.azureResourceType
import org.kikd.azure.resources.iacAttribute
import org.kikd.azure.resources.iacExpression
import org.kikd.azure.resources.infraExpr
import org.kikd.azure.resources.infrastructure
import org.kikd.core.KikdDependencyKind
import org.kikd.core.KikdGraphNodeKind
import org.kikd.core.KikdProjectPlanner
import org.kikd.core.generate
import org.kikd.core.kikdProject
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GenerationIntegrationTest {
    private companion object {
        val exampleWidgetType = azureResourceType("Microsoft.Example/widgets@2024-01-01", "azurerm_example_widget")
    }

    @Test
    fun `generates structured azure pipeline and bicep files`() {
        val output = Files.createTempDirectory("kikd-generation-test")

        generate(
            project = sampleProject(),
            outputDir = output,
            AzurePipelinesYamlBackend(),
            AzureBicepBackend(),
        )

        val pipeline = output.resolve(".azure-pipeline/pipelines/azure-pipelines.yml")
        val stage = output.resolve(".azure-pipeline/stages/build.yml")
        val deployStage = output.resolve(".azure-pipeline/stages/deploy.yml")
        val steps = output.resolve(".azure-pipeline/steps/build-buildapp.yml")
        val bicep = output.resolve("infra/main.bicep")
        val bicepParameters = output.resolve("infra/main.bicepparam")

        assertTrue(Files.exists(pipeline), "Azure Pipelines root YAML should be generated")
        assertTrue(Files.exists(stage), "Azure Pipelines stage template should be generated")
        assertTrue(Files.exists(deployStage), "Dependent Azure Pipelines stage template should be generated")
        assertTrue(Files.exists(steps), "Azure Pipelines step template should be generated")
        assertTrue(Files.exists(bicep), "Bicep file should be generated")
        assertTrue(Files.exists(bicepParameters), "Bicep parameter file should be generated")

        assertContains(pipeline.readText(), "template: ../stages/build.yml")
        assertContains(pipeline.readText(), "template: ../stages/deploy.yml")
        assertContains(stage.readText(), "template: ../steps/build-buildapp.yml")
        assertContains(deployStage.readText(), "dependsOn: Build")
        assertContains(steps.readText(), "script: ./gradlew check")
        assertContains(output.resolve(".azure-pipeline/steps/deploy-deployapp.yml").readText(), "DownloadPipelineArtifact@2")
        assertContains(output.resolve(".azure-pipeline/steps/deploy-deployapp.yml").readText(), "az deployment sub create")
        assertContains(bicep.readText(), "param location string")
        assertContains(bicep.readText(), "Microsoft.Network/virtualNetworks@2025-01-01")
        assertContains(bicep.readText(), "addressPrefixes:")
        assertContains(bicepParameters.readText(), "using './main.bicep'")
    }

    @Test
    fun `generates structured terraform files with one resource per file`() {
        val output = Files.createTempDirectory("kikd-terraform-generation-test")

        generate(
            project = sampleProject(),
            outputDir = output,
            AzureTerraformBackend(),
        )

        val provider = output.resolve("infra/provider.tf")
        val data = output.resolve("infra/data.tf")
        val resourceGroup = output.resolve("infra/resource-group-rg-kikd-test.tf")
        val virtualNetwork = output.resolve("infra/virtual-network-vnet-kikd-test.tf")
        val storageAccount = output.resolve("infra/storage-account-stkikdtest.tf")
        val servicePlan = output.resolve("infra/service-plan-plan-kikd-test.tf")
        val webApp = output.resolve("infra/linux-web-app-app-kikd-test.tf")
        val keyVault = output.resolve("infra/key-vault-kv-kikd-test.tf")

        assertTrue(Files.exists(provider), "Terraform provider file should be generated")
        assertTrue(Files.exists(data), "Terraform data source file should be generated")
        assertTrue(Files.exists(resourceGroup), "Resource group file should be generated")
        assertTrue(Files.exists(virtualNetwork), "Virtual network file should be generated")
        assertTrue(Files.exists(storageAccount), "Storage account file should be generated")
        assertTrue(Files.exists(servicePlan), "Service plan file should be generated")
        assertTrue(Files.exists(webApp), "Web app file should be generated")
        assertTrue(Files.exists(keyVault), "Key vault file should be generated")

        assertContains(provider.readText(), "provider \"azurerm\"")
        assertContains(data.readText(), "data \"azurerm_client_config\" \"current\"")
        assertContains(virtualNetwork.readText(), "resource \"azurerm_virtual_network\" \"vnet_kikd_test\"")
        assertContains(storageAccount.readText(), "resource \"azurerm_storage_account\" \"stkikdtest\"")
    }

    @Test
    fun `bicep generation fails when resource only provides terraform ir`() {
        val output = Files.createTempDirectory("kikd-bicep-capability-test")

        val failure = assertFailsWith<IllegalStateException> {
            generate(
                project = projectWith(TerraformOnlyResource("tf-only")),
                outputDir = output,
                AzureBicepBackend(),
            )
        }

        assertContains(failure.message.orEmpty(), "does not provide Bicep IR")
    }

    @Test
    fun `terraform generation fails when resource only provides bicep ir`() {
        val output = Files.createTempDirectory("kikd-terraform-capability-test")

        val failure = assertFailsWith<IllegalStateException> {
            generate(
                project = projectWith(BicepOnlyResource("bicep-only")),
                outputDir = output,
                AzureTerraformBackend(),
            )
        }

        assertContains(failure.message.orEmpty(), "does not provide Terraform IR")
    }

    @Test
    fun `project planner builds graph with pipeline artifact and infrastructure dependencies`() {
        val plan = KikdProjectPlanner.plan(sampleProject())
        val graph = plan.graph

        assertTrue(graph.nodes.any { it.kind == KikdGraphNodeKind.STAGE && it.label == "Build" })
        assertTrue(graph.nodes.any { it.kind == KikdGraphNodeKind.STAGE && it.label == "Deploy" })
        assertTrue(graph.nodes.any { it.kind == KikdGraphNodeKind.ARTIFACT && it.label == "infra" })
        assertTrue(graph.nodes.any { it.kind == KikdGraphNodeKind.STACK && it.label == "main" })
        assertTrue(graph.nodes.any { it.kind == KikdGraphNodeKind.RESOURCE && it.label == "app-kikd-test" })

        assertTrue(graph.dependencyEdges.any { it.kind == KikdDependencyKind.STAGE_DEPENDS_ON })
        assertTrue(graph.dependencyEdges.any { it.kind == KikdDependencyKind.ARTIFACT_PRODUCED_BY })
        assertTrue(graph.dependencyEdges.any { it.kind == KikdDependencyKind.ARTIFACT_CONSUMED_BY })
        assertTrue(graph.dependencyEdges.any { it.kind == KikdDependencyKind.RESOURCE_DEPENDS_ON })
    }

    @Test
    fun `project planner fails when an artifact is consumed without a producer`() {
        val project = kikdProject {
            azure {
                pipeline {
                    val infraArtifact = artifact("infra", path = "output/infra")
                    stage("Deploy") {
                        job("DeployApp") {
                            downloadPipelineArtifact(infraArtifact)
                        }
                    }
                }

                infrastructure {
                    stack(name = "main") {
                        resourceGroup("rg-kikd-test")
                    }
                }
            }
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            KikdProjectPlanner.plan(project)
        }

        assertContains(failure.message.orEmpty(), "missing producers")
    }

    @Test
    fun `project planner fails when stage dependencies contain a cycle`() {
        val project = kikdProject {
            azure {
                pipeline {
                    stage("A") {
                        dependsOn = "B"
                        job("BuildA") {
                            script("echo A")
                        }
                    }
                    stage("B") {
                        dependsOn = "A"
                        job("BuildB") {
                            script("echo B")
                        }
                    }
                }
            }
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            KikdProjectPlanner.plan(project)
        }

        assertContains(failure.message.orEmpty(), "Dependency cycle detected")
    }

    @Test
    fun `generates bicep with stack parameters`() {
        val output = Files.createTempDirectory("kikd-params-test")

        generate(
            project = projectWithParams(),
            outputDir = output,
            AzureBicepBackend(),
        )

        val bicep = output.resolve("infra/main.bicep").readText()
        val bicepParam = output.resolve("infra/main.bicepparam").readText()

        assertContains(bicep, "param environment string = 'dev'")
        assertContains(bicep, "param namePrefix string = 'kikd'")
        assertContains(bicep, "name: 'rg-' + namePrefix + '-' + environment")
        assertContains(bicep, "scope: resourceGroup(rg__namePrefix___environment_.name)")
        assertContains(bicepParam, "param environment = readEnvironmentVariable('ENVIRONMENT', 'dev')")
        assertContains(bicepParam, "param namePrefix = readEnvironmentVariable('NAMEPREFIX', 'kikd')")
    }

    @Test
    fun `generates nested subnet objects from virtual network dsl`() {
        val output = Files.createTempDirectory("kikd-subnets-test")

        generate(
            project = projectWithSubnets(),
            outputDir = output,
            AzureBicepBackend(),
        )

        val bicep = output.resolve("infra/main.bicep").readText()
        assertContains(bicep, "subnets: [")
        assertContains(bicep, "name: 'Subnet-1'")
        assertContains(bicep, "addressPrefix: '10.0.0.0/24'")
        assertContains(bicep, "name: 'Subnet-2'")
        assertContains(bicep, "addressPrefix: '10.0.1.0/24'")
    }

    @Test
    fun `generates terraform variable blocks for stack parameters`() {
        val output = Files.createTempDirectory("kikd-tf-params-test")

        generate(
            project = projectWithParams(),
            outputDir = output,
            AzureTerraformBackend(),
        )

        val variables = output.resolve("infra/variables.tf")
        assertTrue(Files.exists(variables), "Terraform variables file should be generated")
        val content = variables.readText()
        assertContains(content, "variable \"environment\"")
        assertContains(content, "default = \"dev\"")
        assertContains(content, "variable \"namePrefix\"")
        assertContains(content, "default = \"kikd\"")
        assertContains(output.resolve("infra/resource-group-rg-nameprefix-environment.tf").readText(), "name = \"rg-${'$'}{var.namePrefix}-${'$'}{var.environment}\"")
    }

    @Test
    fun `generates azure yaml with azd config backend`() {
        val output = Files.createTempDirectory("kikd-azd-config-test")

        generate(
            project = projectWithParams(),
            outputDir = output,
            AzdConfigBackend(),
        )

        val azureYaml = output.resolve("azure.yaml")
        assertTrue(Files.exists(azureYaml), "azure.yaml should be generated")
        val content = azureYaml.readText()
        assertContains(content, "name: kikd-project")
        assertContains(content, "provider: bicep")
        assertContains(content, "path: infra")
    }

    @Test
    fun `deployAzd passes parameters as step env vars`() {
        val output = Files.createTempDirectory("kikd-azd-deploy-test")

        generate(
            project = projectWithAzdDeploy(),
            outputDir = output,
            AzurePipelinesYamlBackend(),
        )

        val stepsFile = output.resolve(".azure-pipeline/steps/deploy-deployapp.yml")
        assertTrue(Files.exists(stepsFile), "Deploy steps file should exist")
        val content = stepsFile.readText()
        assertContains(content, "env:")
        assertContains(content, "ENVIRONMENT: dev")
        assertContains(content, "NAMEPREFIX: kikd")
        assertContains(content, "azd provision")
    }

    @Test
    fun `generates azure yaml with variable groups`() {
        val output = Files.createTempDirectory("kikd-variable-group-test")

        generate(
            project = projectWithVariableGroups(),
            outputDir = output,
            AzurePipelinesYamlBackend(),
        )

        val root = output.resolve(".azure-pipeline/pipelines/azure-pipelines.yml").readText()
        val deployStage = output.resolve(".azure-pipeline/stages/deploy.yml").readText()

        assertContains(root, "group: shared-infra")
        assertContains(deployStage, "group: prod-infra")
    }

    @Test
    fun `variable group references expose macro references`() {
        val group = org.kikd.azure.pipelines.PipelineVariableGroup("shared-infra")

        assertEquals("\$(ENVIRONMENT)", group.variable("ENVIRONMENT").macroReference())
    }

    @Test
    fun `inline variable declarations expose macro references`() {
        val environment = org.kikd.azure.pipelines.AzurePipelineBuilder()
            .variable("environment", "dev")

        assertEquals("\$(environment)", environment.macroReference())
    }

    @Test
    fun `generates stage output variables that can be passed into azd deployment`() {
        val output = Files.createTempDirectory("kikd-stage-output-vars-test")

        generate(
            project = projectWithStageOutputVariables(),
            outputDir = output,
            AzurePipelinesYamlBackend(),
        )

        val buildSteps = output.resolve(".azure-pipeline/steps/build-resolveinfra.yml").readText()
        val deployStage = output.resolve(".azure-pipeline/stages/deploy.yml").readText()
        val deploySteps = output.resolve(".azure-pipeline/steps/deploy-deployinfra.yml").readText()

        assertContains(buildSteps, "##vso[task.setvariable variable=environment;isOutput=true]dev")
        assertContains(buildSteps, "name: setInfraValues")
        assertContains(
            deployStage,
            "stageDependencies.Build.ResolveInfra.outputs['setInfraValues.environment']",
        )
        assertContains(deploySteps, "ENVIRONMENT: \$(environment)")
    }

    @Test
    fun `stage output bindings can use a different script variable name`() {
        val output = Files.createTempDirectory("kikd-stage-output-alias-test")

        generate(
            project = projectWithAliasedStageOutputVariable(),
            outputDir = output,
            AzurePipelinesYamlBackend(),
        )

        val deployStage = output.resolve(".azure-pipeline/stages/deploy.yml").readText()
        assertContains(
            deployStage,
            "stageDependencies.Build.ResolveInfra.outputs['setInfraValues.actualEnvironment']",
        )
    }

    @Test
    fun `stage output variables fail when used before assignment`() {
        val failure = assertFailsWith<IllegalStateException> {
            kikdProject {
                azure {
                    pipeline {
                        val environment = stageOutputVariable("environment")
                        stage("Deploy") {
                            variable(environment)
                        }
                    }
                }
            }
        }

        assertContains(failure.message.orEmpty(), "has not been assigned")
    }

    private fun projectWithParams() = kikdProject {
        azure {
            infrastructure {
                stack(name = "main", location = "eastus") {
                    val environment = parameter("environment", defaultValue = "dev")
                    val namePrefix = parameter("namePrefix", defaultValue = "kikd")
                    val rg = resourceGroup(
                        infraExpr("rg-${namePrefix.templateReference()}-${environment.templateReference()}"),
                    )
                    virtualNetwork("vnet-kikd-test", resourceGroup = rg)
                }
            }
        }
    }

    private fun projectWithSubnets() = kikdProject {
        azure {
            infrastructure {
                stack(name = "main", location = "eastus") {
                    val rg = resourceGroup("rg-kikd-subnets")
                    virtualNetwork("vnet-kikd-subnets", resourceGroup = rg) {
                        subnet("Subnet-1", "10.0.0.0/24")
                        subnet("Subnet-2", "10.0.1.0/24")
                    }
                }
            }
        }
    }

    private fun projectWithAzdDeploy() = kikdProject {
        azure {
            pipeline {
                val infraArtifact = artifact("infra", path = "output/infra")
                val build = stage("Build") {
                    job("BuildApp") {
                        pool("ubuntu-latest")
                        checkout()
                        publishPipelineArtifact(infraArtifact)
                    }
                }
                stage("Deploy") {
                    dependsOn(build)
                    job("DeployApp") {
                        pool("ubuntu-latest")
                        downloadPipelineArtifact(infraArtifact)
                        deployAzd(
                            stackName = "main",
                            params = mapOf("environment" to "dev", "namePrefix" to "kikd"),
                            artifact = infraArtifact,
                        )
                    }
                }
            }
            infrastructure {
                stack(name = "main", location = "eastus") {
                    parameter("environment", defaultValue = "dev")
                    parameter("namePrefix", defaultValue = "kikd")
                    resourceGroup("rg-kikd-test")
                }
            }
        }
    }

    private fun projectWithVariableGroups() = kikdProject {
        azure {
            pipeline {
                val sharedInfra = variableGroup("shared-infra")
                val environment = sharedInfra.variable("ENVIRONMENT")
                stage("Deploy") {
                    val prodInfra = variableGroup("prod-infra")
                    val namePrefix = prodInfra.variable("NAMEPREFIX")
                    job("DeployApp") {
                        script("echo ${environment.macroReference()} ${namePrefix.macroReference()}")
                    }
                }
            }
        }
    }

    private fun projectWithStageOutputVariables() = kikdProject {
        azure {
            pipeline {
                val infraArtifact = artifact("infra", path = "output/infra")
                val environmentOutput = stageOutputVariable("environment")
                val build = stage("Build") {
                    job("ResolveInfra") {
                        bash(
                            "echo \"##vso[task.setvariable variable=environment;isOutput=true]dev\"",
                            stageOutput(environmentOutput, "environment"),
                        ) {
                            raw("name", "setInfraValues")
                        }
                        publishPipelineArtifact(infraArtifact)
                    }
                }
                stage("Deploy") {
                    dependsOn(build)
                    val environment = variable(environmentOutput)
                    job("DeployInfra") {
                        downloadPipelineArtifact(infraArtifact)
                        deployAzd(
                            stackName = "main",
                            params = mapOf("environment" to environment.macroReference()),
                            artifact = infraArtifact,
                        )
                    }
                }
            }
            infrastructure {
                stack(name = "main") {
                    resourceGroup("rg-kikd-stage-output")
                }
            }
        }
    }

    private fun projectWithAliasedStageOutputVariable() = kikdProject {
        azure {
            pipeline {
                val environmentOutput = stageOutputVariable("environment")
                val build = stage("Build") {
                    job("ResolveInfra") {
                        bash(
                            "echo \"##vso[task.setvariable variable=actualEnvironment;isOutput=true]dev\"",
                            stageOutput(environmentOutput, "actualEnvironment"),
                        ) {
                            raw("name", "setInfraValues")
                        }
                    }
                }
                stage("Deploy") {
                    dependsOn(build)
                    variable(environmentOutput)
                }
            }
        }
    }

    private fun sampleProject() = kikdProject {
        azure {
            pipeline {
                val infraArtifact = artifact("infra", path = "output/infra")
                trigger {
                    branches {
                        include("main")
                    }
                }
                val vmImage = variable("vmImage", "ubuntu-latest")
                val build = stage("Build") {
                    job("BuildApp") {
                        pool(vmImage.macroReference())
                        checkout()
                        script("./gradlew check") {
                            displayName = "Run checks"
                        }
                        publishPipelineArtifact(infraArtifact)
                    }
                }
                stage("Deploy") {
                    dependsOn(build)
                    job("DeployApp") {
                        pool(vmImage.macroReference())
                        downloadPipelineArtifact(infraArtifact)
                        deployBicep(stackName = "main", artifact = infraArtifact)
                    }
                }
            }

            infrastructure {
                stack(name = "main", location = "eastus") {
                    val rg = resourceGroup("rg-kikd-test")
                    virtualNetwork("vnet-kikd-test", resourceGroup = rg)
                    storageAccount("stkikdtest", resourceGroup = rg)
                    val plan = appServicePlan("plan-kikd-test", resourceGroup = rg)
                    webApp("app-kikd-test", resourceGroup = rg, servicePlan = plan)
                    keyVault(
                        name = "kv-kikd-test",
                        resourceGroup = rg,
                        tenantIdExpression = "tenant().tenantId",
                    )
                }
            }
        }
    }

    private fun projectWith(resource: AzureResource) = kikdProject {
        azure {
            infrastructure {
                stack {
                    resource(resource)
                }
            }
        }
    }

    private class TerraformOnlyResource(
        override val logicalName: String,
    ) : AzureResource, AzureInfraResource {
        override val name: String = logicalName

        override fun terraformSpec(context: AzureTerraformIrContext): AzureTerraformResourceSpec =
            AzureTerraformResourceSpec(
                logicalName = logicalName,
                type = AzureResourceTypes.ResourceGroup.terraform,
                attributes = listOf(
                    iacAttribute("name", name),
                    iacAttribute("location", iacExpression("var.location")),
                ),
            )
    }

    private class BicepOnlyResource(
        override val logicalName: String,
    ) : AzureResource, AzureInfraResource {
        override val name: String = logicalName
        override val dependsOn: List<AzureResourceReference> = emptyList()

        override fun bicepSpec(context: AzureBicepIrContext): AzureBicepResourceSpec =
            AzureBicepResourceSpec(
                symbol = logicalName,
                type = exampleWidgetType.bicep,
                attributes = listOf(iacAttribute("name", name)),
            )
    }
}
