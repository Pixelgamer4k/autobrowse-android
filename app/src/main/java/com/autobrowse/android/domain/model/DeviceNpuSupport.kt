package com.autobrowse.android.domain.model

import com.autobrowse.android.util.SystemPropertyReader

data class LocalLlmModelArtifact(
    val fileName: String,
    val downloadUrl: String,
    val sizeLabel: String? = null,
)

data class NpuSupportStatus(
    val supported: Boolean,
    val socModel: String,
    val artifact: LocalLlmModelArtifact? = null,
    val reason: String? = null,
)

object DeviceNpuSupport {
    private const val GEMMA_E2B_BASE =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main"

    fun socModel(): String =
        SystemPropertyReader.get("ro.soc.model")
            .ifBlank { SystemPropertyReader.get("ro.board.platform") }
            .lowercase()
            .trim()

    fun status(model: LocalLlmModel): NpuSupportStatus {
        val soc = socModel().ifBlank { "unknown" }
        if (model != LocalLlmModel.GEMMA_4_E2B) {
            return NpuSupportStatus(
                supported = false,
                socModel = soc,
                reason = "Only Gemma 4 E2B has NPU builds today. Use GPU for E4B.",
            )
        }

        val artifact = resolveNpuArtifact(model)
        return if (artifact != null) {
            NpuSupportStatus(
                supported = true,
                socModel = soc,
                artifact = artifact,
            )
        } else {
            NpuSupportStatus(
                supported = false,
                socModel = soc,
                reason = buildUnsupportedReason(soc),
            )
        }
    }

    fun resolveNpuArtifact(model: LocalLlmModel): LocalLlmModelArtifact? {
        if (model != LocalLlmModel.GEMMA_4_E2B) return null
        val variantSuffix = resolveVariantSuffix(socModel()) ?: return null
        val fileName = "gemma-4-E2B-it_$variantSuffix.litertlm"
        return LocalLlmModelArtifact(
            fileName = fileName,
            downloadUrl = "$GEMMA_E2B_BASE/$fileName",
            sizeLabel = sizeLabelFor(variantSuffix),
        )
    }

    fun isNpuModelFile(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return name.contains("_qualcomm_") ||
            name.contains("_google_tensor_") ||
            name.contains("_intel_")
    }

    fun modelMatchesBackend(path: String, backend: LlmBackend): Boolean = when (backend) {
        LlmBackend.NPU -> path.isNotBlank() && isNpuModelFile(path)
        LlmBackend.CPU, LlmBackend.GPU -> path.isBlank() || !isNpuModelFile(path)
    }

    private fun resolveVariantSuffix(soc: String): String? {
        val normalized = soc.lowercase().trim()
        if (normalized.isBlank()) return null

        return when {
            normalized.contains("sm8750") -> "qualcomm_sm8750"
            normalized.contains("qcs8275") || normalized.contains("iq-8275") -> "qualcomm_qcs8275"
            normalized.contains("tensor_g5") ||
                normalized.contains("google_tensor_g5") ||
                (normalized.contains("tensor") && normalized.contains("g5")) -> "Google_Tensor_G5"
            else -> null
        }
    }

    private fun sizeLabelFor(variantSuffix: String): String = when (variantSuffix) {
        "qualcomm_sm8750" -> "~2.8 GB"
        "qualcomm_qcs8275" -> "~3.1 GB"
        "Google_Tensor_G5" -> "~3.7 GB"
        else -> "~3 GB"
    }

    private fun buildUnsupportedReason(soc: String): String {
        val chip = soc.ifBlank { "unknown" }
        return "No Gemma 4 E2B NPU build for this device ($chip). " +
            "Use GPU with the standard download, or import a SoC-specific .litertlm from HuggingFace."
    }
}