package org.kikd.azure.resources.render

fun azureSymbol(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_]"), "_").let {
        if (it.firstOrNull()?.isDigit() == true) "_$it" else it
    }

fun azureFileSlug(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "unnamed" }

internal fun String.escapeBicep(): String = replace("'", "''")

internal fun String.escapeHcl(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.hclKey(): String =
    if (matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) this else "\"${escapeHcl()}\""
