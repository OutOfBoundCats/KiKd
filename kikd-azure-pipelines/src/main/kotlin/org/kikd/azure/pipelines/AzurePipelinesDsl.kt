package org.kikd.azure.pipelines

import org.kikd.azure.AzureBuilder
import org.kikd.azure.AzureCloud
import org.kikd.azure.component

fun AzureBuilder.pipeline(block: AzurePipelineBuilder.() -> Unit) {
    register(AzurePipelineComponent(AzurePipelineBuilder().apply(block).build()))
}

fun AzureCloud.pipeline(): AzurePipeline? = component<AzurePipelineComponent>()?.pipeline
