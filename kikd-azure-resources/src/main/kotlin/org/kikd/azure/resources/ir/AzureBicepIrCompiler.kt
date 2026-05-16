package org.kikd.azure.resources.ir

import org.kikd.azure.resources.render.azureSymbol
import org.kikd.azure.resources.render.escapeBicep

class AzureBicepIrCompiler {
    fun renderResource(spec: AzureBicepResourceSpec): String = buildString {
        appendLine("resource ${azureSymbol(spec.symbol)} '${spec.type.value}' = {")
        spec.attributes.forEach { append(renderAttribute(it, 2)) }
        append("}")
    }

    fun renderParameterValue(value: String): String =
        "'${value.escapeBicep()}'"

    private fun renderAttribute(attribute: IacAttribute, indent: Int): String =
        "${spaces(indent)}${bicepKey(attribute.name)}: ${renderValue(attribute.value, indent)}\n"

    private fun renderValue(value: IacValue, indent: Int): String = when (value) {
        IacValue.Null -> "null"
        is IacValue.StringLiteral -> "'${value.value.escapeBicep()}'"
        is IacValue.NumberLiteral -> value.value.toString()
        is IacValue.BooleanLiteral -> value.value.toString()
        is IacValue.Expression -> value.expression
        is IacValue.ArrayLiteral -> renderArray(value.values, indent)
        is IacValue.ObjectLiteral -> renderObject(value.attributes, indent)
    }

    private fun renderArray(values: List<IacValue>, indent: Int): String =
        if (values.isEmpty()) {
            "[]"
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
                attributes.forEach { append(renderAttribute(it, indent + 2)) }
                append("${spaces(indent)}}")
            }
        }

    private fun bicepKey(value: String): String =
        if (value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) value else "'${value.escapeBicep()}'"

    private fun spaces(count: Int): String = " ".repeat(count)
}
