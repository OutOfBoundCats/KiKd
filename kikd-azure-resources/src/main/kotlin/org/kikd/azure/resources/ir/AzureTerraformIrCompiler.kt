package org.kikd.azure.resources.ir

import org.kikd.azure.resources.render.azureSymbol
import org.kikd.azure.resources.render.escapeHcl
import org.kikd.azure.resources.render.hclKey

class AzureTerraformIrCompiler {
    fun renderResource(spec: AzureTerraformResourceSpec): String = buildString {
        appendLine("resource \"${spec.type.value}\" \"${azureSymbol(spec.logicalName)}\" {")
        spec.attributes.forEach { append(renderAttribute(it, 2)) }
        append("}")
    }

    private fun renderAttribute(attribute: IacAttribute, indent: Int): String =
        "${spaces(indent)}${attribute.name} = ${renderValue(attribute.value, indent)}\n"

    private fun renderValue(value: IacValue, indent: Int): String = when (value) {
        IacValue.Null -> "null"
        is IacValue.StringLiteral -> "\"${value.value.escapeHcl()}\""
        is IacValue.NumberLiteral -> value.value.toString()
        is IacValue.BooleanLiteral -> value.value.toString()
        is IacValue.Expression -> value.expression
        is IacValue.ArrayLiteral -> renderArray(value.values, indent)
        is IacValue.ObjectLiteral -> renderObject(value.attributes, indent)
    }

    private fun renderArray(values: List<IacValue>, indent: Int): String =
        if (values.isEmpty()) {
            "[]"
        } else if (values.all { it !is IacValue.ObjectLiteral && it !is IacValue.ArrayLiteral }) {
            values.joinToString(prefix = "[", postfix = "]") { renderValue(it, indent) }
        } else {
            buildString {
                appendLine("[")
                values.forEach { value ->
                    append(spaces(indent + 2))
                    append(renderValue(value, indent + 2))
                    appendLine()
                }
                append("${spaces(indent)}]")
            }
        }

    private fun renderObject(attributes: List<IacAttribute>, indent: Int): String =
        if (attributes.isEmpty()) {
            "{}"
        } else {
            buildString {
                appendLine("{")
                attributes.forEach { attribute ->
                    append(spaces(indent + 2))
                    append(attribute.name.hclKey())
                    append(" = ")
                    append(renderValue(attribute.value, indent + 2))
                    appendLine()
                }
                append("${spaces(indent)}}")
            }
        }

    private fun spaces(count: Int): String = " ".repeat(count)
}
