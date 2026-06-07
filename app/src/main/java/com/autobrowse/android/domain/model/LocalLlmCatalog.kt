package com.autobrowse.android.domain.model

data class LocalLlmModelInfo(
    val model: LocalLlmModel,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val pageUrl: String,
    val modelFileName: String,
    val modelDownloadUrl: String,
    val mmprojFileName: String,
    val mmprojDownloadUrl: String,
) {
    val defaultFileName: String get() = modelFileName
    val downloadUrl: String get() = modelDownloadUrl
}

object LocalLlmCatalog {
    private const val QWEN_0_8B = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF"
    private const val QWEN_2B = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF"
    private const val QWEN_4B = "https://huggingface.co/unsloth/Qwen3.5-4B-GGUF"
    private const val GEMMA_E2B = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF"
    private const val GEMMA_E4B = "https://huggingface.co/unsloth/gemma-4-E4B-it-GGUF"

    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_5_0_8B,
            displayName = "Qwen3.5 0.8B",
            description = "Experimental · ultra-light · expect 6–10 min responses on phone",
            sizeLabel = "~740 MB (Q4 + vision)",
            pageUrl = QWEN_0_8B,
            modelFileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
            modelDownloadUrl = "$QWEN_0_8B/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf",
            mmprojFileName = "Qwen3.5-0.8B-mmproj-F16.gguf",
            mmprojDownloadUrl = "$QWEN_0_8B/resolve/main/mmproj-F16.gguf",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_5_2B,
            displayName = "Qwen3.5 2B",
            description = "Experimental · balanced · cloud API recommended for daily use",
            sizeLabel = "~1.8 GB (Q4 + vision)",
            pageUrl = QWEN_2B,
            modelFileName = "Qwen3.5-2B-Q4_K_M.gguf",
            modelDownloadUrl = "$QWEN_2B/resolve/main/Qwen3.5-2B-Q4_K_M.gguf",
            mmprojFileName = "Qwen3.5-2B-mmproj-F16.gguf",
            mmprojDownloadUrl = "$QWEN_2B/resolve/main/mmproj-F16.gguf",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.QWEN3_5_4B,
            displayName = "Qwen3.5 4B",
            description = "Experimental · slower · 6–10+ min typical on mobile",
            sizeLabel = "~3.4 GB (Q4 + vision)",
            pageUrl = QWEN_4B,
            modelFileName = "Qwen3.5-4B-Q4_K_M.gguf",
            modelDownloadUrl = "$QWEN_4B/resolve/main/Qwen3.5-4B-Q4_K_M.gguf",
            mmprojFileName = "Qwen3.5-4B-mmproj-F16.gguf",
            mmprojDownloadUrl = "$QWEN_4B/resolve/main/mmproj-F16.gguf",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "Google edge model · vision + audio + tools · 128K ctx",
            sizeLabel = "~4.1 GB (Q4 + vision)",
            pageUrl = GEMMA_E2B,
            modelFileName = "gemma-4-E2B-it-Q4_K_M.gguf",
            modelDownloadUrl = "$GEMMA_E2B/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
            mmprojFileName = "gemma-4-E2B-it-mmproj-F16.gguf",
            mmprojDownloadUrl = "$GEMMA_E2B/resolve/main/mmproj-F16.gguf",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E4B,
            displayName = "Gemma 4 E4B",
            description = "Stronger Gemma edge · vision + audio + tools · 128K ctx",
            sizeLabel = "~6.5 GB (Q4 + vision)",
            pageUrl = GEMMA_E4B,
            modelFileName = "gemma-4-E4B-it-Q4_K_M.gguf",
            modelDownloadUrl = "$GEMMA_E4B/resolve/main/gemma-4-E4B-it-Q4_K_M.gguf",
            mmprojFileName = "gemma-4-E4B-it-mmproj-F16.gguf",
            mmprojDownloadUrl = "$GEMMA_E4B/resolve/main/mmproj-F16.gguf",
        ),
    )

    fun infoFor(model: LocalLlmModel): LocalLlmModelInfo =
        models.first { it.model == model }
}