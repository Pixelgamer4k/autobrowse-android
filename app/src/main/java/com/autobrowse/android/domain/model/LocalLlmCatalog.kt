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

private const val DEFAULT_MIN_CONTEXT_TOKENS = 1_024
private const val DEFAULT_MAX_CONTEXT_TOKENS = 131_072

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
    val minContextTokens: Int = DEFAULT_MIN_CONTEXT_TOKENS,
    val maxContextTokens: Int = DEFAULT_MAX_CONTEXT_TOKENS,
    val communityNote: String? = null,
) {
    val defaultFileName: String get() = modelFileName
    val downloadUrl: String get() = modelDownloadUrl
    val isCommunity: Boolean get() = source == LocalLlmSource.COMMUNITY
}

object LocalLlmCatalog {
    private const val GEMMA_E2B = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA_E4B = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"

    const val MIN_CONTEXT_TOKENS = DEFAULT_MIN_CONTEXT_TOKENS
    const val MAX_CONTEXT_TOKENS = DEFAULT_MAX_CONTEXT_TOKENS
    const val CONTEXT_STEP_TOKENS = 1_024

    /** @deprecated Use [DeviceContextDefaults.defaultContextTokens] for RAM-aware defaults. */
    const val DEFAULT_CONTEXT_TOKENS = 16_384

    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "Fast · multimodal",
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
            description = "Stronger reasoning",
            sizeLabel = "~3.7 GB",
            pageUrl = GEMMA_E4B,
            modelFileName = "gemma-4-E4B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E4B/resolve/main/gemma-4-E4B-it.litertlm",
            source = LocalLlmSource.OFFICIAL,
            toolCalling = ToolCallingLevel.NATIVE,
            vision = VisionLevel.MULTIMODAL,
        ),
    )

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