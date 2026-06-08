package com.autobrowse.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.autobrowse.android.domain.model.LlmBackend
import com.autobrowse.android.domain.model.LlmConfig
import com.autobrowse.android.domain.model.LlmProvider
import com.autobrowse.android.domain.model.DeviceContextDefaults
import com.autobrowse.android.domain.model.LocalLlmCatalog
import com.autobrowse.android.domain.model.LocalLlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureSettingsStore(private val appContext: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        "autobrowse_secure_prefs",
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun getLlmConfig(): LlmConfig = withContext(Dispatchers.IO) {
        val localModel = migrateLocalModel(
            prefs.getString(KEY_LOCAL_MODEL, LocalLlmModel.GEMMA_4_E2B.name),
        )
        val savedTokens = if (prefs.contains(KEY_MAX_TOKENS)) {
            prefs.getInt(KEY_MAX_TOKENS, LocalLlmCatalog.DEFAULT_CONTEXT_TOKENS)
        } else {
            DeviceContextDefaults.defaultContextTokens(appContext, localModel)
        }
        LlmConfig(
            provider = LlmProvider.valueOf(
                prefs.getString(KEY_PROVIDER, LlmProvider.REMOTE.name) ?: LlmProvider.REMOTE.name,
            ),
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL,
            modelId = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            localModel = localModel,
            backend = migrateBackend(prefs.getString(KEY_BACKEND, LlmBackend.GPU.name)),
            localModelPath = migrateLocalModelPath(
                prefs.getString(KEY_LOCAL_MODEL_PATH, "") ?: "",
            ),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            maxTokens = LocalLlmCatalog.coerceContextTokens(localModel, savedTokens),
        )
    }

    suspend fun saveLlmConfig(config: LlmConfig) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_API_URL, config.apiUrl)
            .putString(KEY_MODEL_ID, config.modelId)
            .putString(KEY_LOCAL_MODEL, config.localModel.name)
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_LOCAL_MODEL_PATH, config.localModelPath)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(
                KEY_MAX_TOKENS,
                LocalLlmCatalog.coerceContextTokens(config.localModel, config.maxTokens),
            )
            .apply()
    }

    suspend fun getEnabledSkills(): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_ENABLED_SKILLS, DEFAULT_SKILLS) ?: DEFAULT_SKILLS
    }

    suspend fun saveEnabledSkills(skills: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY_ENABLED_SKILLS, skills).apply()
    }

    private fun migrateLocalModel(raw: String?): LocalLlmModel =
        runCatching { LocalLlmModel.valueOf(raw ?: LocalLlmModel.GEMMA_4_E2B.name) }
            .getOrDefault(LocalLlmModel.GEMMA_4_E2B)

    private fun migrateBackend(raw: String?): LlmBackend {
        if (raw.equals("NPU", ignoreCase = true)) return LlmBackend.GPU
        return runCatching { LlmBackend.valueOf(raw ?: LlmBackend.GPU.name) }
            .getOrDefault(LlmBackend.GPU)
    }

    private fun migrateLocalModelPath(path: String): String {
        if (path.isBlank()) return ""
        val file = java.io.File(path)
        return when {
            file.isFile && path.endsWith(".litertlm", ignoreCase = true) -> path
            else -> ""
        }
    }

    companion object {
        private const val KEY_PROVIDER = "llm_provider"
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_API_URL = "llm_api_url"
        private const val KEY_MODEL_ID = "llm_model_id"
        private const val KEY_LOCAL_MODEL = "llm_local_model"
        private const val KEY_BACKEND = "llm_backend"
        private const val KEY_LOCAL_MODEL_PATH = "llm_local_model_path"
        private const val KEY_TEMPERATURE = "llm_temperature"
        private const val KEY_MAX_TOKENS = "llm_max_tokens"
        private const val KEY_ENABLED_SKILLS = "enabled_skills"

        private const val DEFAULT_API_URL = "https://api.openai.com/v1/"
        private const val DEFAULT_MODEL = "gpt-4o-mini"

        private val DEFAULT_SKILLS = setOf(
            "WEB_REQUEST",
            "DATA_EXTRACTION",
            "FORM_FILL",
            "SUMMARIZE",
            "BACKGROUND_TASK",
        )
    }
}