package org.kikd.examples

import org.kikd.azure.azure
import org.kikd.azure.pipelines.AzurePipelinesYamlBackend
import org.kikd.azure.pipelines.pipeline
import org.kikd.azure.resources.AzdConfigBackend
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infraExpr
import org.kikd.azure.resources.infrastructure
import org.kikd.core.kikdProject

object StageVariablesExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val project = kikdProject {
            azure {
                pipeline {
                    val infraArtifact = artifact("infra", path = "infra")
                    val build = stage("Build") {
                        job("PublishInfra") {
                            pool("ubuntu-latest")
                            publishPipelineArtifact(infraArtifact)
                        }
                    }
                    stage("Deploy") {
                        dependsOn(build)
                        val environment = variable("environment", "dev")
                        val namePrefix = variable("namePrefix", "kikd")
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
                        val environment = parameter("environment", defaultValue = "dev")
                        val namePrefix = parameter("namePrefix", defaultValue = "kikd")
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
            "03-stage-variables",
            project,
            AzurePipelinesYamlBackend(),
            AzureBicepBackend(),
            AzdConfigBackend(),
        )
    }
}
