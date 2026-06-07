package com.autobrowse.android.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.autobrowse.android.domain.model.LlmConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureSettingsStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "autobrowse_secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    suspend fun getLlmConfig(): LlmConfig = withContext(Dispatchers.IO) {
        LlmConfig(
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL,
            modelId = prefs.getString(KEY_MODEL_ID, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 4096),
        )
    }

    suspend fun saveLlmConfig(config: LlmConfig) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_API_KEY, config.apiKey)
            .putString(KEY_API_URL, config.apiUrl)
            .putString(KEY_MODEL_ID, config.modelId)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .apply()
    }

    suspend fun getEnabledSkills(): Set<String> = withContext(Dispatchers.IO) {
        prefs.getStringSet(KEY_ENABLED_SKILLS, DEFAULT_SKILLS) ?: DEFAULT_SKILLS
    }

    suspend fun saveEnabledSkills(skills: Set<String>) = withContext(Dispatchers.IO) {
        prefs.edit().putStringSet(KEY_ENABLED_SKILLS, skills).apply()
    }

    companion object {
        private const val KEY_API_KEY = "llm_api_key"
        private const val KEY_API_URL = "llm_api_url"
        private const val KEY_MODEL_ID = "llm_model_id"
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