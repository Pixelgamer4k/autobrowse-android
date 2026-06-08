package com.autobrowse.android.domain.model

enum class LocalLlmSource {
    OFFICIAL,
    COMMUNITY,
}

enum class ToolCallingLevel {
    NATIVE,
    SPECIALIST,
    UNVERIFIED,
    NONE,
}

enum class VisionLevel {
    MULTIMODAL,
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
    val source: LocalLlmSource,
    val toolCalling: ToolCallingLevel,
    val vision: VisionLevel,
    val minContextTokens: Int = MIN_CONTEXT_TOKENS,
    val maxContextTokens: Int = MAX_CONTEXT_TOKENS,
    val communityNote: String? = null,
) {
    val defaultFileName: String get() = modelFileName
    val downloadUrl: String get() = modelDownloadUrl
    val isCommunity: Boolean get() = source == LocalLlmSource.COMMUNITY
}

object LocalLlmCatalog {
    private const val GEMMA_E2B = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA_E4B = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"
    private const val FUNCTIONGEMMA =
        "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions"
    private const val QWEN3_5_0_8B = "https://huggingface.co/GabrieleConte/Qwen3.5-0.8B-LiteRT"
    private const val QWEN3_5_2B = "https://huggingface.co/paulsp94/Qwen3.5-2B-LiteRT-LM"

    const val MIN_CONTEXT_TOKENS = 1_024
    const val MAX_CONTEXT_TOKENS = 131_072
    const val CONTEXT_STEP_TOKENS = 1_024

    /** @deprecated Use [DeviceContextDefaults.defaultContextTokens] for RAM-aware defaults. */
    const val DEFAULT_CONTEXT_TOKENS = 16_384

    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "Default agent · fast · multimodal",
            sizeLabel = "~2.6 GB",
            pageUrl = GEMMA_E2B,
            modelFileName = "gemma-4-E2B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E2B/resolve/main/gemma-4-E2B-it.litertlm",
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E4B,
            displayName = "Gemma 4 E4B",
            description = "Stronger reasoning · multimodal",
            sizeLabel = "~3.7 GB",
            pageUrl = GEMMA_E4B,
            modelFileName = "gemma-4-E4B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E4B/resolve/main/gemma-4-E4B-it.litertlm",
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.FUNCTIONGEMMA_270M,
            displayName = "FunctionGemma 270M",
            description = "Best tool accuracy · text-only",
            sizeLabel = "~289 MB",
            pageUrl = FUNCTIONGEMMA,
            modelFileName = "mobile_actions_q8_ekv1024.litertlm",
            modelDownloadUrl = "$FUNCTIONGEMMA/resolve/main/mobile_actions_q8_ekv1024.litertlm",
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.SPECIALIST,
            vision = VisionLevel.NONE,
            minContextTokens = 8_192,
            maxContextTokens = 16_384,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_5_0_8B,
            displayName = "Qwen3.5 0.8B",
            description = "Community LiteRT port · multimodal · experimental",
            sizeLabel = "~1.1 GB",
            pageUrl = QWEN3_5_0_8B,
            modelFileName = "qwen35_mm_q8_ekv2048.litertlm",
            modelDownloadUrl = "$QWEN3_5_0_8B/resolve/main/qwen35_mm_q8_ekv2048.litertlm",
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.MULTIMODAL,
            communityNote = "GabrieleConte port — Qwen3.5 runtime support may be limited.",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_5_2B,
            displayName = "Qwen3.5 2B",
            description = "Community LiteRT port · text · experimental",
            sizeLabel = "~1.8 GB",
            pageUrl = QWEN3_5_2B,
            modelFileName = "qwen35_2b.litertlm",
            modelDownloadUrl = "$QWEN3_5_2B/resolve/main/qwen35_2b.litertlm",
            source = LocalLlmSource.COMMUNITY,
            toolCalling = ToolCallingLevel.UNVERIFIED,
            vision = VisionLevel.NONE,
            communityNote = "paulsp94 port — verify tool calling on your device.",
        ),
    )

    val officialModels: List<LocalLlmModelInfo> =
        models.filter { it.source == LocalLlmSource.OFFICIAL }

    val communityModels: List<LocalLlmModelInfo> =
        models.filter { it.source == LocalLlmSource.COMMUNITY }

    fun infoFor(model: LocalLlmModel): LocalLlmModelInfo =
        models.first { it.model == model }

    fun contextBounds(model: LocalLlmModel): IntRange {
        val info = infoFor(model)
        return info.minContextTokens..info.maxContextTokens
    }

    fun coerceContextTokens(model: LocalLlmModel, tokens: Int): Int {
        val bounds = contextBounds(model)
        val stepped = (tokens / CONTEXT_STEP_TOKENS) * CONTEXT_STEP_TOKENS
        return stepped.coerceIn(bounds.first, bounds.last)
    }
}