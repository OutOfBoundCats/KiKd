package org.kikd.examples

import org.kikd.azure.azure
import org.kikd.azure.pipelines.AzurePipelinesYamlBackend
import org.kikd.azure.pipelines.pipeline
import org.kikd.azure.resources.AzdConfigBackend
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infraExpr
import org.kikd.azure.resources.infrastructure
import org.kikd.core.kikdProject

object VariableGroupExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val project = kikdProject {
            azure {
                pipeline {
                    val sharedInfra = variableGroup("shared-infra")
                    val environment = sharedInfra.variable("ENVIRONMENT")
                    val namePrefix = sharedInfra.variable("NAMEPREFIX")
                    val infraArtifact = artifact("infra", path = "infra")
                    val build = stage("Build") {
                        job("PublishInfra") {
                            pool("ubuntu-latest")
                            publishPipelineArtifact(infraArtifact)
                        }
                    }
                    stage("Deploy") {
                        dependsOn(build)
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
                        val plan = appServicePlan(
                            infraExpr("plan-${namePrefix.templateReference()}-${environment.templateReference()}"),
                            resourceGroup = rg,
                        )
                        webApp(
                            infraExpr("app-${namePrefix.templateReference()}-${environment.templateReference()}"),
                            resourceGroup = rg,
                            servicePlan = plan,
                        )
                    }
                }
            }
        }

        generateExample(
            "04-variable-group",
            project,
            AzurePipelinesYamlBackend(),
            AzureBicepBackend(),
            AzdConfigBackend(),
        )
    }
}
