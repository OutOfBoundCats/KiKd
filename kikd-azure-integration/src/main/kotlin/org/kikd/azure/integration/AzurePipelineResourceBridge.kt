package org.kikd.azure.integration

import org.kikd.azure.pipelines.PipelineParameter
import org.kikd.azure.resources.InfraParameter
import org.kikd.azure.resources.dsl.AzureStackBuilder

fun AzureStackBuilder.parameter(parameter: PipelineParameter): InfraParameter =
    parameter(name = parameter.name, type = parameter.type, defaultValue = parameter.default)
