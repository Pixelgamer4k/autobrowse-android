package com.autobrowse.android.vpc.tools

import com.autobrowse.android.agent.tools.AgentTool
import com.autobrowse.android.agent.tools.ToolExecutionContext
import com.autobrowse.android.agent.tools.ToolExecutionResult
import com.autobrowse.android.vpc.core.VirtualPCManager
import kotlin.time.Duration.Companion.seconds

class VpcStatusTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_status"
    override val description = "Report Virtual PC provisioning and runtime state."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult =
        ToolExecutionResult(vpc.statusSummary())
}

class VpcStartTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_start"
    override val description = "Start the Virtual PC (Ubuntu). Set desktop=false for headless mode."
    override val parametersJson = """
        {"type":"object","properties":{"desktop":{"type":"boolean","default":true}}}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val desktop = args["desktop"]?.toString()?.toBooleanStrictOrNull() ?: true
        return vpc.start(desktop = desktop).fold(
            onSuccess = { ToolExecutionResult("Virtual PC started (desktop=$desktop)") },
            onFailure = { ToolExecutionResult(it.message ?: "start failed", success = false) },
        )
    }
}

class VpcStopTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_stop"
    override val description = "Stop the Virtual PC environment."
    override val parametersJson = """{"type":"object","properties":{}}"""

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        vpc.stop()
        return ToolExecutionResult("Virtual PC stopped")
    }
}

class VpcExecTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_exec"
    override val description = "Execute a shell command inside the Virtual PC (Ubuntu)."
    override val parametersJson = """
        {"type":"object","properties":{
          "command":{"type":"string"},
          "timeout_sec":{"type":"integer","default":120},
          "workdir":{"type":"string","default":"/root"}
        },"required":["command"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val command = args["command"]?.toString()
            ?: return ToolExecutionResult("command required", success = false)
        val timeout = args["timeout_sec"]?.toString()?.toIntOrNull()?.seconds ?: 120.seconds
        val workdir = args["workdir"]?.toString() ?: "/root"
        val result = vpc.execute(command, timeout, workdir)
        return ToolExecutionResult(result.summary(), success = result.exitCode == 0 && !result.timedOut)
    }
}

class VpcWriteFileTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_write_file"
    override val description = "Write a file inside the Virtual PC filesystem."
    override val parametersJson = """
        {"type":"object","properties":{
          "path":{"type":"string"},
          "content":{"type":"string"}
        },"required":["path","content"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val path = args["path"]?.toString() ?: return ToolExecutionResult("path required", success = false)
        val content = args["content"]?.toString() ?: return ToolExecutionResult("content required", success = false)
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val result = vpc.execute("""echo '$b64' | base64 -d > "$path"""")
        return ToolExecutionResult(result.summary(), success = result.exitCode == 0)
    }
}

class VpcReadFileTool(private val vpc: VirtualPCManager) : AgentTool {
    override val name = "vpc_read_file"
    override val description = "Read a file from the Virtual PC filesystem."
    override val parametersJson = """
        {"type":"object","properties":{
          "path":{"type":"string"},
          "max_bytes":{"type":"integer","default":65536}
        },"required":["path"]}
    """.trimIndent()

    override suspend fun execute(args: Map<String, Any?>, context: ToolExecutionContext): ToolExecutionResult {
        val path = args["path"]?.toString() ?: return ToolExecutionResult("path required", success = false)
        val max = args["max_bytes"]?.toString()?.toIntOrNull() ?: 65536
        val result = vpc.execute("""head -c $max "$path" | base64 -w0""")
        return ToolExecutionResult(result.stdout.text, success = result.exitCode == 0)
    }
}

fun createVpcTools(vpc: VirtualPCManager): List<AgentTool> = listOf(
    VpcStatusTool(vpc),
    VpcStartTool(vpc),
    VpcStopTool(vpc),
    VpcExecTool(vpc),
    VpcWriteFileTool(vpc),
    VpcReadFileTool(vpc),
)