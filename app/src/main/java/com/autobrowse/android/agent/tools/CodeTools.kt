package com.autobrowse.android.agent.tools

import com.autobrowse.android.agent.runtime.CodeSandbox
import com.autobrowse.android.agent.runtime.ToolBridge

class ExecuteCodeTool(
    private val registryProvider: () -> ToolRegistry,
    private val documentGenerator: DocumentGenerator,
) : AgentTool {
    override val name = "execute_code"
    override val description = "Execute Python-like code with tool_call() RPC, matplotlib charts, and PDF generation."
    override val parametersJson = """
        {"type":"object","properties":{"language":{"type":"string","enum":["python","kotlin"]},"code":{"type":"string"}},"required":["code"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val code = args["code"]?.toString().orEmpty()
        if (code.isBlank()) return ToolExecutionResult("code required", success = false)
        val bridge = ToolBridge(registryProvider(), context)
        val sandbox = CodeSandbox(bridge, documentGenerator)
        val output = sandbox.executePython(code)
        return ToolExecutionResult(output.take(8000))
    }
}

class PythonExecuteTool(
    private val registryProvider: () -> ToolRegistry,
    private val documentGenerator: DocumentGenerator,
) : AgentTool {
    override val name = "python_execute"
    override val description = "Run a Python script with matplotlib (→ chart), reportlab/fpdf (→ PDF), and tool_call() bridge."
    override val parametersJson = """
        {"type":"object","properties":{"script":{"type":"string"}},"required":["script"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val script = args["script"]?.toString().orEmpty()
        val bridge = ToolBridge(registryProvider(), context)
        val sandbox = CodeSandbox(bridge, documentGenerator)
        val output = sandbox.executePython(script)
        return ToolExecutionResult(output.take(8000))
    }
}

class PdfGenerateTool(private val documentGenerator: DocumentGenerator) : AgentTool {
    override val name = "pdf_generate"
    override val description = "Generate a PDF document from title and body text."
    override val parametersJson = """
        {"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val title = args["title"]?.toString().orEmpty()
        val content = args["content"]?.toString().orEmpty()
        val path = documentGenerator.generatePdf(title, content)
        context.extractedData["generated_pdf"] = path
        return ToolExecutionResult("PDF generated: $path")
    }
}

class ChartGenerateTool(private val documentGenerator: DocumentGenerator) : AgentTool {
    override val name = "chart_generate"
    override val description = "Generate a chart image (matplotlib-style) from labels and values."
    override val parametersJson = """
        {"type":"object","properties":{"title":{"type":"string"},"labels":{"type":"array","items":{"type":"string"}},"values":{"type":"array","items":{"type":"number"}}},"required":["title","labels","values"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val title = args["title"]?.toString().orEmpty()
        @Suppress("UNCHECKED_CAST")
        val labels = (args["labels"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        val values = (args["values"] as? List<*>)?.mapNotNull { it?.toString()?.toDoubleOrNull() }.orEmpty()
        val path = documentGenerator.generateChart(title, labels, values)
        context.extractedData["generated_chart"] = path
        return ToolExecutionResult("Chart saved: $path")
    }
}