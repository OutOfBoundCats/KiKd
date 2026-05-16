package org.kikd.examples

import org.kikd.azure.azure
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infrastructure
import org.kikd.core.kikdProject

object SimpleInfrastructureExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val project = kikdProject {
            azure {
                infrastructure {
                    stack(name = "main", location = "eastus") {
                        resourceGroup("rg-kikd-simple")
                    }
                }
            }
        }

        generateExample("01-simple-infra", project, AzureBicepBackend())
    }
}
