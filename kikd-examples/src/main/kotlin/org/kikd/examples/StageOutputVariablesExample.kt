package org.kikd.examples

import org.kikd.azure.azure
import org.kikd.azure.pipelines.AzurePipelinesYamlBackend
import org.kikd.azure.pipelines.pipeline
import org.kikd.azure.resources.AzdConfigBackend
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infraExpr
import org.kikd.azure.resources.infrastructure
import org.kikd.core.kikdProject

object StageOutputVariablesExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val project = kikdProject {
            azure {
                pipeline {
                    val infraArtifact = artifact("infra", path = "infra")
                    val environmentOutput = stageOutputVariable("environment")
                    val namePrefixOutput = stageOutputVariable("namePrefix")
                    val build = stage("Build") {
                        job("ResolveInfra") {
                            pool("ubuntu-latest")
                            bash(
                                """
                                echo "##vso[task.setvariable variable=resolvedEnvironment;isOutput=true]dev"
                                echo "##vso[task.setvariable variable=resolvedNamePrefix;isOutput=true]kikd"
                                """.trimIndent(),
                                stageOutput(environmentOutput, "resolvedEnvironment"),
                                stageOutput(namePrefixOutput, "resolvedNamePrefix"),
                            ) {
                                displayName = "Resolve infrastructure values"
                                raw("name", "setInfraValues")
                            }
                            publishPipelineArtifact(infraArtifact)
                        }
                    }
                    stage("Deploy") {
                        dependsOn(build)
                        val environment = variable(environmentOutput)
                        val namePrefix = variable(namePrefixOutput)
                        job("DeployInfra") {
                            pool("ubuntu-latest")
                            downloadPipelineArtifact(infraArtifact)
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
                }

                infrastructure {
                    stack(name = "main", location = "eastus") {
                        val environment = parameter("environment")
                        val namePrefix = parameter("namePrefix")
                        val rg = resourceGroup(
                            infraExpr("rg-${namePrefix.templateReference()}-${environment.templateReference()}"),
                        )
                        storageAccount(
                            infraExpr("st${namePrefix.templateReference()}${environment.templateReference()}"),
                            resourceGroup = rg,
                        )
                    }
                }
            }
        }

        generateExample(
            "05-stage-output-variables",
            project,
            AzurePipelinesYamlBackend(),
            AzureBicepBackend(),
            AzdConfigBackend(),
        )
    }
}
