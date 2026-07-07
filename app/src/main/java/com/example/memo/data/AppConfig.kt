package com.example.memo.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 模型供应商
 * DSK: DeepSeek
 * GLM: 智谱 BigModel
 * ALIYUN: 阿里云 DashScope (Qwen / liveportrait)
 */
enum class ModelProvider(val key: String) {
    DSK("dsk"),
    GLM("glm"),
    ALIYUN("aliyun");

    companion object {
        fun fromKey(key: String?): ModelProvider {
            return values().firstOrNull { it.key == key } ?: DSK
        }
    }
}

object AppConfig {

    private const val PREFS_NAME = "memo_app_config"

    // 当前激活的模型供应商
    private const val KEY_PROVIDER = "model_provider"

    // DeepSeek 配置
    private const val KEY_DSK_API_URL = "dsk_api_url"
    private const val KEY_DSK_MODEL = "dsk_model"
    private const val KEY_DSK_API_KEY = "dsk_api_key"

    // 智谱 GLM 配置
    private const val KEY_GLM_API_URL = "glm_api_url"
    private const val KEY_GLM_MODEL = "glm_model"
    private const val KEY_GLM_API_KEY_ID = "glm_api_key_id"
    private const val KEY_GLM_SECRET = "glm_secret"

    // 阿里云 DashScope 配置
    private const val KEY_ALIYUN_API_URL = "aliyun_api_url"
    private const val KEY_ALIYUN_MODEL = "aliyun_model"
    private const val KEY_ALIYUN_API_KEY = "aliyun_api_key"

    private const val DEFAULT_DSK_API_URL = "https://api.deepseek.com/v1/chat/completions"
    private const val DEFAULT_DSK_MODEL = "deepseek-chat"
    private const val DEFAULT_DSK_API_KEY = "你的DeepSeek API Key"

    private const val DEFAULT_GLM_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    private const val DEFAULT_GLM_MODEL = "glm-4.7-flash"
    private const val DEFAULT_GLM_API_KEY_ID = "你的智谱 GLM API Key ID"
    private const val DEFAULT_GLM_SECRET = "你的智谱 GLM Secret"

    // 阿里云 DashScope OpenAI 兼容模式
    // 默认 qwen3.5-omni-plus-2026-03-15：通义千问全模态大模型，支持图片、音频、视频多模态输入
    private const val DEFAULT_ALIYUN_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val DEFAULT_ALIYUN_MODEL = "qwen3.5-omni-plus-2026-03-15"
    private const val DEFAULT_ALIYUN_API_KEY = "你的阿里云 DashScope API Key"

    // 旧版本默认模型（liveportrait 不在 OpenAI 兼容模式支持列表里，会 404）
    // 用于 init() 中检测本地缓存的旧值并一次性迁移到新默认值
    private const val DEPRECATED_ALIYUN_MODEL = "liveportrait"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        // 一次性迁移：本地缓存的阿里云模型如果还是已废弃的 liveportrait，
        // 自动覆盖为新默认 qwen3.5-omni-plus-2026-03-15，避免 404。
        // 仅在检测到旧默认值时才覆盖，用户手动改过的其他值不会被影响。
        if (getPrefs().getString(KEY_ALIYUN_MODEL, null) == DEPRECATED_ALIYUN_MODEL) {
            getPrefs().edit().putString(KEY_ALIYUN_MODEL, DEFAULT_ALIYUN_MODEL).apply()
        }
    }

    private fun getPrefs(): SharedPreferences {
        return prefs ?: throw IllegalStateException("AppConfig not initialized. Call AppConfig.init(context) first.")
    }

    // ===== 当前供应商 =====

    var provider: ModelProvider
        get() = ModelProvider.fromKey(getPrefs().getString(KEY_PROVIDER, ModelProvider.DSK.key))
        set(value) {
            getPrefs().edit().putString(KEY_PROVIDER, value.key).apply()
        }

    // ===== DeepSeek 配置 =====

    var dskApiUrl: String
        get() = getPrefs().getString(KEY_DSK_API_URL, DEFAULT_DSK_API_URL) ?: DEFAULT_DSK_API_URL
        set(value) {
            getPrefs().edit().putString(KEY_DSK_API_URL, value).apply()
        }

    var dskModel: String
        get() = getPrefs().getString(KEY_DSK_MODEL, DEFAULT_DSK_MODEL) ?: DEFAULT_DSK_MODEL
        set(value) {
            getPrefs().edit().putString(KEY_DSK_MODEL, value).apply()
        }

    var dskApiKey: String
        get() = getPrefs().getString(KEY_DSK_API_KEY, DEFAULT_DSK_API_KEY) ?: DEFAULT_DSK_API_KEY
        set(value) {
            getPrefs().edit().putString(KEY_DSK_API_KEY, value).apply()
        }

    fun saveDskConfig(apiUrl: String, model: String, apiKey: String) {
        dskApiUrl = apiUrl
        dskModel = model
        dskApiKey = apiKey
    }

    // ===== 智谱 GLM 配置 =====

    var glmApiUrl: String
        get() = getPrefs().getString(KEY_GLM_API_URL, DEFAULT_GLM_API_URL) ?: DEFAULT_GLM_API_URL
        set(value) {
            getPrefs().edit().putString(KEY_GLM_API_URL, value).apply()
        }

    var glmModel: String
        get() = getPrefs().getString(KEY_GLM_MODEL, DEFAULT_GLM_MODEL) ?: DEFAULT_GLM_MODEL
        set(value) {
            getPrefs().edit().putString(KEY_GLM_MODEL, value).apply()
        }

    var glmApiKeyId: String
        get() = getPrefs().getString(KEY_GLM_API_KEY_ID, DEFAULT_GLM_API_KEY_ID) ?: DEFAULT_GLM_API_KEY_ID
        set(value) {
            getPrefs().edit().putString(KEY_GLM_API_KEY_ID, value).apply()
        }

    var glmSecret: String
        get() = getPrefs().getString(KEY_GLM_SECRET, DEFAULT_GLM_SECRET) ?: DEFAULT_GLM_SECRET
        set(value) {
            getPrefs().edit().putString(KEY_GLM_SECRET, value).apply()
        }

    /**
     * 智谱 API 完整 Key 格式：{API Key ID}.{secret}
     */
    val glmFullApiKey: String
        get() = "${glmApiKeyId}.${glmSecret}"

    fun saveGlmConfig(apiUrl: String, model: String, apiKeyId: String, secret: String) {
        glmApiUrl = apiUrl
        glmModel = model
        glmApiKeyId = apiKeyId
        glmSecret = secret
    }

    // ===== 阿里云 DashScope 配置 =====

    var aliyunApiUrl: String
        get() = getPrefs().getString(KEY_ALIYUN_API_URL, DEFAULT_ALIYUN_API_URL) ?: DEFAULT_ALIYUN_API_URL
        set(value) {
            getPrefs().edit().putString(KEY_ALIYUN_API_URL, value).apply()
        }

    var aliyunModel: String
        get() = getPrefs().getString(KEY_ALIYUN_MODEL, DEFAULT_ALIYUN_MODEL) ?: DEFAULT_ALIYUN_MODEL
        set(value) {
            getPrefs().edit().putString(KEY_ALIYUN_MODEL, value).apply()
        }

    var aliyunApiKey: String
        get() = getPrefs().getString(KEY_ALIYUN_API_KEY, DEFAULT_ALIYUN_API_KEY) ?: DEFAULT_ALIYUN_API_KEY
        set(value) {
            getPrefs().edit().putString(KEY_ALIYUN_API_KEY, value).apply()
        }

    fun saveAliyunConfig(apiUrl: String, model: String, apiKey: String) {
        aliyunApiUrl = apiUrl
        aliyunModel = model
        aliyunApiKey = apiKey
    }

    // ===== 向后兼容（保留旧 API 以便其他模块不报错） =====

    var apiUrl: String
        get() = when (provider) {
            ModelProvider.GLM -> glmApiUrl
            ModelProvider.ALIYUN -> aliyunApiUrl
            else -> dskApiUrl
        }
        set(value) {
            when (provider) {
                ModelProvider.GLM -> glmApiUrl = value
                ModelProvider.ALIYUN -> aliyunApiUrl = value
                else -> dskApiUrl = value
            }
        }

    var model: String
        get() = when (provider) {
            ModelProvider.GLM -> glmModel
            ModelProvider.ALIYUN -> aliyunModel
            else -> dskModel
        }
        set(value) {
            when (provider) {
                ModelProvider.GLM -> glmModel = value
                ModelProvider.ALIYUN -> aliyunModel = value
                else -> dskModel = value
            }
        }

    var apiKey: String
        get() = when (provider) {
            ModelProvider.GLM -> glmFullApiKey
            ModelProvider.ALIYUN -> aliyunApiKey
            else -> dskApiKey
        }
        set(value) {
            when (provider) {
                ModelProvider.GLM -> {
                    val parts = value.split(".", limit = 2)
                    if (parts.size == 2) {
                        glmApiKeyId = parts[0]
                        glmSecret = parts[1]
                    }
                }
                ModelProvider.ALIYUN -> aliyunApiKey = value
                else -> dskApiKey = value
            }
        }

    @Deprecated("请使用 saveDskConfig / saveGlmConfig / saveAliyunConfig 区分供应商", ReplaceWith("saveDskConfig(newApiUrl, newModel, newApiKey)"))
    fun saveConfig(newApiUrl: String, newModel: String, newApiKey: String) {
        when (provider) {
            ModelProvider.GLM -> saveGlmConfig(newApiUrl, newModel, glmApiKeyId, glmSecret)
            ModelProvider.ALIYUN -> saveAliyunConfig(newApiUrl, newModel, newApiKey)
            else -> saveDskConfig(newApiUrl, newModel, newApiKey)
        }
    }
}
