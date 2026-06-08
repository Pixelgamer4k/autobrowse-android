package com.autobrowse.android.domain.model

data class LocalLlmModelInfo(
    val model: LocalLlmModel,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val pageUrl: String,
    val modelFileName: String,
    val modelDownloadUrl: String,
    val contextTokens: Int,
) {
    val defaultFileName: String get() = modelFileName
    val downloadUrl: String get() = modelDownloadUrl
}

object LocalLlmCatalog {
    private const val GEMMA_E2B = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    private const val GEMMA_E4B = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"

    const val DEFAULT_CONTEXT_TOKENS = 16_384
    const val MAX_CONTEXT_TOKENS = 32_768

    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "Fast on-device · vision + audio + tools · up to 32K context",
            sizeLabel = "~2.6 GB",
            pageUrl = GEMMA_E2B,
            modelFileName = "gemma-4-E2B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E2B/resolve/main/gemma-4-E2B-it.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E4B,
            displayName = "Gemma 4 E4B",
            description = "Stronger reasoning · vision + audio + tools · up to 32K context",
            sizeLabel = "~3.7 GB",
            pageUrl = GEMMA_E4B,
            modelFileName = "gemma-4-E4B-it.litertlm",
            modelDownloadUrl = "$GEMMA_E4B/resolve/main/gemma-4-E4B-it.litertlm",
            contextTokens = DEFAULT_CONTEXT_TOKENS,
        ),
    )

    fun infoFor(model: LocalLlmModel): LocalLlmModelInfo =
        models.first { it.model == model }

    fun resolveArtifact(model: LocalLlmModel, backend: LlmBackend): LocalLlmModelArtifact {
        val info = infoFor(model)
        if (backend != LlmBackend.NPU) {
            return LocalLlmModelArtifact(
                fileName = info.modelFileName,
                downloadUrl = info.modelDownloadUrl,
                sizeLabel = info.sizeLabel,
            )
        }

        return DeviceNpuSupport.resolveNpuArtifact(model)
            ?: throw IllegalStateException(DeviceNpuSupport.status(model).reason)
    }

    fun npuSupportStatus(model: LocalLlmModel): NpuSupportStatus =
        DeviceNpuSupport.status(model)
}