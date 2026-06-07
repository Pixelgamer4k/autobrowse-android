package com.autobrowse.android.data.local

import android.content.Context
import com.autobrowse.android.data.remote.ChatMessageDto
import com.autobrowse.android.data.remote.LlmCompletion
import com.autobrowse.android.domain.model.AttachmentPayload
import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.ToolDefinition
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LocalLlmService(
    private val context: Context,
    private val modelFileManager: ModelFileManager,
) {
    private val engineMutex = Mutex()
    private var cachedEngine: Engine? = null
    private var cachedConfigKey: String? = null

    suspend fun testConnection(config: LlmConfig): String = withContext(Dispatchers.IO) {
        requireModelFile(config)
        withConversation(config) { conversation ->
            val response = conversation.sendMessage("Reply with OK.")
            val text = messageText(response).orEmpty()
            if (text.isNotBlank()) {
                "Connected on ${config.backend.name} — model replied: ${text.take(80)}"
            } else {
                "Connected on ${config.backend.name} — model responded successfully."
            }
        }
    }

    suspend fun complete(
        config: LlmConfig,
        systemPrompt: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolDefinition> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
    ): LlmCompletion = withContext(Dispatchers.IO) {
        requireModelFile(config)
        if (attachmentPayload.attachments.isNotEmpty()) {
            throw IllegalStateException("Local LiteRT-LM models do not support attachments yet.")
        }

        withConversation(
            config = config,
            systemPrompt = systemPrompt,
            history = messages.dropLast(1),
        ) { conversation ->
            val lastUser = messages.lastOrNull { it.role == "user" }?.content
                ?: throw IllegalStateException("No user message to send.")
            val response = conversation.sendMessage(lastUser)
            LlmCompletion(
                content = messageText(response),
                toolCalls = emptyList(),
                finishReason = "stop",
            )
        }
    }

    suspend fun chat(
        config: LlmConfig,
        systemPrompt: String,
        userPrompt: String,
        history: List<ChatMessageDto> = emptyList(),
        attachmentPayload: AttachmentPayload = AttachmentPayload(),
    ): String = complete(
        config = config,
        systemPrompt = systemPrompt,
        messages = history + ChatMessageDto(role = "user", content = userPrompt),
        attachmentPayload = attachmentPayload,
    ).content ?: throw IllegalStateException("No completion returned from local model.")

    fun close() {
        cachedEngine?.close()
        cachedEngine = null
        cachedConfigKey = null
    }

    private suspend fun <T> withConversation(
        config: LlmConfig,
        systemPrompt: String = "You are a helpful assistant.",
        history: List<ChatMessageDto> = emptyList(),
        block: suspend (com.google.ai.edge.litertlm.Conversation) -> T,
    ): T = withEngine(config) { engine ->
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                initialMessages = history.mapNotNull { dto ->
                    when (dto.role) {
                        "user" -> Message.user(dto.content.orEmpty())
                        "assistant" -> dto.content?.let { Message.model(it) }
                        else -> null
                    }
                },
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = config.temperature.toDouble(),
                ),
            ),
        ).use { conversation ->
            block(conversation)
        }
    }

    private suspend fun <T> withEngine(config: LlmConfig, block: suspend (Engine) -> T): T =
        engineMutex.withLock {
            val key = engineCacheKey(config)
            val engine = if (cachedConfigKey == key && cachedEngine != null) {
                cachedEngine!!
            } else {
                cachedEngine?.close()
                val created = Engine(
                    EngineConfig(
                        modelPath = config.localModelPath,
                        backend = toBackend(config.backend),
                        cacheDir = context.cacheDir.absolutePath,
                    ),
                )
                created.initialize()
                cachedEngine = created
                cachedConfigKey = key
                created
            }
            block(engine)
        }

    private fun requireModelFile(config: LlmConfig) {
        if (!modelFileManager.modelFileExists(config.localModelPath)) {
            throw IllegalStateException(
                "Model file not found. Download a .litertlm file and import it on the setup screen.",
            )
        }
    }

    private fun engineCacheKey(config: LlmConfig): String =
        "${config.localModelPath}|${config.backend}|${config.localModel}"

    private fun messageText(message: Message): String? {
        val text = message.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }
            .trim()
        return text.ifBlank { message.toString().trim().ifBlank { null } }
    }

    private fun toBackend(backend: LlmBackend): Backend = when (backend) {
        LlmBackend.CPU -> Backend.CPU()
        LlmBackend.GPU -> Backend.GPU()
        LlmBackend.NPU -> Backend.NPU(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
        )
    }
}