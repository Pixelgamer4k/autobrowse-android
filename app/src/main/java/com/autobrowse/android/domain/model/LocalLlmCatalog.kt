package com.autobrowse.android.domain.model

enum class LocalLlmSource {
    OFFICIAL,
    COMMUNITY,
}

enum class ToolCallingLevel {
    /** Native LiteRT tool-calling protocol — reliable for browser agents. */
    NATIVE,
    /** Purpose-built function-calling model (may have narrow action vocabulary). */
    SPECIALIST,
    /** May work; verify on-device before relying on browser tools. */
    UNVERIFIED,
    /** Not suitable for tool-using agents. */
    NONE,
}

enum class VisionLevel {
    /** Vision (+ optional audio) in the same .litertlm bundle. */
    MULTIMODAL,
    /** Text-only — screenshots and page images won't be understood. */
    NONE,
}

data class LocalLlmModelInfo(
    val model: LocalLlmModel,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val pageUrl: String,
    val modelFileName: String,
    val modelDownloadUrl: String,
    val contextTokens: Int,
    val source: LocalLlmSource,
    val toolCalling: ToolCallingLevel,
    val vision: VisionLevel,
    val communityNote: String? = null,
) {
    val defaultFileName: String get() = modelFileName
    val downloadUrl: String get() = modelDownloadUrl
    val isCommunity: Boolean get() = source == LocalLlmSource.COMMUNITY

    val toolCallingLabel: String
        get() = when (toolCalling) {
            ToolCallingLevel.NATIVE -> "Tool calling: native"
            ToolCallingLevel.SPECIALIST -> "Tool calling: specialist (best FC, text-only)"
            ToolCallingLevel.UNVERIFIED -> "Tool calling: unverified — test before use"
            ToolCallingLevel.NONE -> "Tool calling: not supported"
        }

    val visionLabel: String
        get() = when (vision) {
            VisionLevel.MULTIMODAL -> "Vision: yes (+ audio where supported)"
            VisionLevel.NONE -> "Vision: no (text-only)"
        }
}

object LocalLlmCatalog {
    private const val GEMMA_E2B = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA_E4B = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"
    private const val FUNCTIONGEMMA =
        "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions"
    private const val QWEN3_0_6B = "https://huggingface.co/litert-community/Qwen3-0.6B"
    private const val QWEN2_5_1_5B = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct"
    private const val AGENT_GEMMA = "https://huggingface.co/kontextdev/agent-gemma"
    private const val MINICPM5 = "https://huggingface.co/lyafence/MiniCPM5-1B-SFT-litertlm"
    private const val LOCOOPERATOR = "https://huggingface.co/4ntoine/LocoOperator-4B-LiteRTLM"
    private const val PEPPX_UNCENSORED = "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm"
    private const val VISION_CROP = "https://huggingface.co/alvarog1318/gemma4-vision-crop-litertlm"
    private const val KOREAN_AUDIO = "https://huggingface.co/psymon/gilbeot-korean-audio-litertlm"

    const val DEFAULT_CONTEXT_TOKENS = 16_384
    const val MAX_CONTEXT_TOKENS = 32_768

    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "Default on-device agent · fast · vision + audio + native tools",
            sizeLabel = "~2.6 GB",
            pageUrl = GEMMA_E2B,
            modelFileName = "gemma-4-E2B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E2B/resolve/main/gemma-4-E2B-it.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E4B,
            displayName = "Gemma 4 E4B",
            description = "Stronger reasoning · vision + audio + native tools",
            sizeLabel = "~3.7 GB",
            pageUrl = GEMMA_E4B,
            modelFileName = "gemma-4-E4B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E4B/resolve/main/gemma-4-E4B-it.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.FUNCTIONGEMMA_270M,
            displayName = "FunctionGemma 270M",
            description = "Smallest model · best function-calling accuracy · 1K context · no vision",
            sizeLabel = "~289 MB",
            pageUrl = FUNCTIONGEMMA,
            modelFileName = "mobile_actions_q8_ekv1024.litertlm",
            modelDownloadUrl = "$FUNCTIONGEMMA/resolve/main/mobile_actions_q8_ekv1024.litertlm",
            contextTokens = 1_024,
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.SPECIALIST,
            vision = VisionLevel.NONE,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_0_6B,
            displayName = "Qwen3 0.6B",
            description = "Lightweight text model · fast · tool calling may have runtime issues",
            sizeLabel = "~475 MB",
            pageUrl = QWEN3_0_6B,
            modelFileName = "qwen3_0_6b_mixed_int4.litertlm",
            modelDownloadUrl = "$QWEN3_0_6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            contextTokens = 8_192,
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.NONE,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN2_5_1_5B,
            displayName = "Qwen2.5 1.5B Instruct",
            description = "Text-only middleweight backup · Qwen2.5 tool protocol",
            sizeLabel = "~1.5 GB",
            pageUrl = QWEN2_5_1_5B,
            modelFileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            modelDownloadUrl =
                "$QWEN2_5_1_5B/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            contextTokens = 4_096,
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.NONE,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.AGENT_GEMMA,
            displayName = "Agent Gemma",
            description = "Gemma 3n E2B fine-tuned for on-device function calling · vision + audio",
            sizeLabel = "~3.4 GB",
            pageUrl = AGENT_GEMMA,
            modelFileName = "gemma-3n-E2B-it-agent.litertlm",
            modelDownloadUrl = "$AGENT_GEMMA/resolve/main/gemma-3n-E2B-it-agent.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
            communityNote = "Third-party fine-tune by kontextdev — not published by Google.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.MINICPM5_1B,
            displayName = "MiniCPM5 1B SFT",
            description = "Long-context text model · XML-style tools in Think variant — standard SFT bundled",
            sizeLabel = "~1.0 GB",
            pageUrl = MINICPM5,
            modelFileName = "MiniCPM5-1B-SFT.litertlm",
            modelDownloadUrl = "$MINICPM5/resolve/main/MiniCPM5-1B-SFT.litertlm",
            contextTokens = 8_192,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.NONE,
            communityNote = "Community conversion by lyafence — verify browser tool calls on your device.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.LOCOOPERATOR_4B,
            displayName = "LocoOperator 4B",
            description = "Code exploration agent (Read/Grep/Bash) — not tuned for browser automation",
            sizeLabel = "~3.8 GB",
            pageUrl = LOCOOPERATOR,
            modelFileName = "model.litertlm",
            modelDownloadUrl = "$LOCOOPERATOR/resolve/main/model.litertlm",
            contextTokens = 8_192,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.NONE,
            communityNote = "Specialist coder agent by 4ntoine — browser tools not validated.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA4_E2B_UNCENSORED,
            displayName = "Gemma 4 E2B Uncensored",
            description = "Alternate Gemma 4 E2B tuning · native tools + vision · different safety profile",
            sizeLabel = "~2.4 GB",
            pageUrl = PEPPX_UNCENSORED,
            modelFileName = "gemma-4-E2B-it-Uncensored-MAX.litertlm",
            modelDownloadUrl = "$PEPPX_UNCENSORED/resolve/main/gemma-4-E2B-it-Uncensored-MAX.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
            communityNote = "Uncensored fork by PeppX — unofficial weights and behavior vs Google release.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA4_VISION_CROP,
            displayName = "Gemma 4 Vision Crop",
            description = "Vision-focused Gemma 4 derivative · likely native tools",
            sizeLabel = "~2.5 GB",
            pageUrl = VISION_CROP,
            modelFileName = "model.litertlm",
            modelDownloadUrl = "$VISION_CROP/resolve/main/model.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.MULTIMODAL,
            communityNote = "Community vision-tuned build by alvarog1318 — not officially supported.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA4_KOREAN_AUDIO,
            displayName = "Gemma 4 Korean + Audio",
            description = "Korean locale LoRA on Gemma 4 E2B · vision + audio",
            sizeLabel = "~2.4 GB",
            pageUrl = KOREAN_AUDIO,
            modelFileName = "gemma-4-E2B-it.litertlm",
            modelDownloadUrl = "$KOREAN_AUDIO/resolve/main/gemma-4-E2B-it.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.MULTIMODAL,
            communityNote = "Locale-specific community build by psymon — verify tools in your language.",
        ),
    )

    val officialModels: List<LocalLlmModelInfo> =
        models.filter { it.source == LocalLlmSource.OFFICIAL }

    val communityModels: List<LocalLlmModelInfo> =
        models.filter { it.source == LocalLlmSource.COMMUNITY }

    fun infoFor(model: LocalLlmModel): LocalLlmModelInfo =
        models.first { it.model == model }
}