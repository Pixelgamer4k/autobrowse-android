package com.autobrowse.android.domain.model

data class LocalLlmModelInfo(
    val model: LocalLlmModel,
    val displayName: String,
    val description: String,
    val sizeLabel: String,
    val pageUrl: String,
    val defaultFileName: String,
    val downloadUrl: String,
)

object LocalLlmCatalog {
    val models: List<LocalLlmModelInfo> = listOf(
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E2B,
            displayName = "Gemma 4 E2B",
            description = "2B effective params · fast on-device baseline",
            sizeLabel = "~2.6 GB",
            pageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            defaultFileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        ),
        LocalLlmModelInfo(
            model = LocalLlmModel.GEMMA_4_E4B,
            displayName = "Gemma 4 E4B",
            description = "4B effective params · stronger reasoning, heavier",
            sizeLabel = "~3.7 GB",
            pageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
            defaultFileName = "gemma-4-E4B-it.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        ),
    )

    fun infoFor(model: LocalLlmModel): LocalLlmModelInfo =
        models.first { it.model == model }
}