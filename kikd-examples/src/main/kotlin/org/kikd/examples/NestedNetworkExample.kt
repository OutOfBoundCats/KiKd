package org.kikd.examples

import org.kikd.azure.azure
import org.kikd.azure.resources.AzureBicepBackend
import org.kikd.azure.resources.infrastructure
import org.kikd.core.kikdProject

object NestedNetworkExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val project = kikdProject {
            azure {
                infrastructure {
                    stack(name = "main", location = "eastus") {
                        val rg = resourceGroup("rg-kikd-network")
                        virtualNetwork("vnet-kikd-network", resourceGroup = rg) {
                            addressPrefixes.clear()
                            addressPrefixes += "10.0.0.0/16"
                            subnet("Subnet-1", "10.0.0.0/24")
                            subnet("Subnet-2", "10.0.1.0/24")
                        }
                    }
                }
            }
        }

        generateExample("02-nested-network", project, AzureBicepBackend())
    }
}
