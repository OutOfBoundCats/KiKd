package org.kikd.azure.resources.api

data class BicepExpression(val expression: String)
data class TerraformExpression(val expression: String)

fun bicepExpression(expression: String): BicepExpression = BicepExpression(expression)
fun terraformExpression(expression: String): TerraformExpression = TerraformExpression(expression)
