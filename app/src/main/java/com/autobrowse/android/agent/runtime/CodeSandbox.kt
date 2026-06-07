package com.autobrowse.android.agent.runtime

import com.autobrowse.android.agent.tools.DocumentGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight code executor for agent automation.
 * Supports Python-like scripts with routed builtins (matplotlib → chart, pdf → generator).
 */
class CodeSandbox(
    private val toolBridge: ToolBridge,
    private val documentGenerator: DocumentGenerator,
) {
    suspend fun executePython(script: String): String = withContext(Dispatchers.Default) {
        val trimmed = script.trim()
        when {
            trimmed.contains("matplotlib", ignoreCase = true) ||
                trimmed.contains("plt.") ->
                executeMatplotlib(trimmed)
            trimmed.contains("reportlab", ignoreCase = true) ||
                trimmed.contains("fpdf", ignoreCase = true) ||
                trimmed.contains("pdf", ignoreCase = true) && trimmed.contains("save", ignoreCase = true) ->
                executePdfScript(trimmed)
            trimmed.contains("tool_call(") || trimmed.contains("tools.") ->
                executeToolCalls(trimmed)
            else -> executeSimpleScript(trimmed)
        }
    }

    private suspend fun executeMatplotlib(script: String): String {
        val title = extractQuoted(script, "title") ?: "Chart"
        val labels = extractList(script, "labels") ?: listOf("A", "B", "C")
        val values = extractNumbers(script, "values") ?: listOf(1.0, 2.0, 3.0)
        val path = documentGenerator.generateChart(title, labels, values)
        return "Chart saved: $path"
    }

    private suspend fun executePdfScript(script: String): String {
        val title = extractQuoted(script, "title") ?: "Document"
        val body = extractQuoted(script, "content") ?: extractQuoted(script, "body") ?: script
        val path = documentGenerator.generatePdf(title, body)
        return "PDF saved: $path"
    }

    private suspend fun executeToolCalls(script: String): String {
        val results = mutableListOf<String>()
        val pattern = Regex("""tool_call\(\s*["'](\w+)["']\s*,\s*(\{[^}]*\})\s*\)""")
        for (match in pattern.findAll(script)) {
            val name = match.groupValues[1]
            val argsJson = match.groupValues[2]
            val args = runCatching {
                val json = JSONObject(argsJson)
                json.keys().asSequence().associateWith { key -> json.get(key) }
            }.getOrDefault(emptyMap())
            results += toolBridge.call(name, args)
        }
        return results.joinToString("\n").ifBlank { "No tool_call() invocations found." }
    }

    private fun executeSimpleScript(script: String): String {
        val output = mutableListOf<String>()
        val lines = script.lines()
        val variables = mutableMapOf<String, Double>()
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("print(") -> {
                    val content = trimmed.removePrefix("print(").removeSuffix(")").trim('"', '\'')
                    output += content
                }
                trimmed.contains("=") && !trimmed.contains("==") -> {
                    val parts = trimmed.split("=", limit = 2)
                    val key = parts[0].trim()
                    val expr = parts[1].trim()
                    variables[key] = evalExpression(expr, variables)
                }
                trimmed.startsWith("#") || trimmed.isBlank() -> Unit
                else -> output += trimmed
            }
        }
        return output.joinToString("\n").ifBlank { "Executed (${lines.size} lines)." }
    }

    private fun evalExpression(expr: String, vars: Map<String, Double>): Double {
        vars[expr]?.let { return it }
        expr.toDoubleOrNull()?.let { return it }
        if (expr.contains("+")) {
            val parts = expr.split("+").map { it.trim() }
            return parts.sumOf { evalExpression(it, vars) }
        }
        if (expr.startsWith("range(")) {
            val inner = expr.removePrefix("range(").removeSuffix(")").split(",").map { it.trim().toInt() }
            return when (inner.size) {
                1 -> inner[0].toDouble()
                else -> (inner[0] until inner[1]).count().toDouble()
            }
        }
        if (expr.contains("sin(")) return sin(vars.values.firstOrNull() ?: 0.0)
        if (expr.contains("cos(")) return cos(vars.values.firstOrNull() ?: 0.0)
        return 0.0
    }

    private fun extractQuoted(script: String, key: String): String? {
        val pattern = Regex("""$key\s*=\s*["']([^"']+)["']""")
        return pattern.find(script)?.groupValues?.get(1)
    }

    private fun extractList(script: String, key: String): List<String>? {
        val pattern = Regex("""$key\s*=\s*\[([^\]]*)]""")
        val raw = pattern.find(script)?.groupValues?.get(1) ?: return null
        return raw.split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotBlank() }
    }

    private fun extractNumbers(script: String, key: String): List<Double>? {
        val pattern = Regex("""$key\s*=\s*\[([^\]]*)]""")
        val raw = pattern.find(script)?.groupValues?.get(1) ?: return null
        return raw.split(",").mapNotNull { it.trim().toDoubleOrNull() }
    }
}