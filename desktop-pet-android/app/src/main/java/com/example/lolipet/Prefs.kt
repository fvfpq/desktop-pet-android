package com.example.lolipet

import android.content.Context
import android.content.SharedPreferences

/** 应用配置存储（SharedPreferences 封装） */
object Prefs {
    private const val FILE = "loli_pet_prefs"
    const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    const val DEFAULT_MODEL = "deepseek-chat"
    const val DEFAULT_PERSONA = "你是一个活泼可爱、有点调皮的二次元萝莉桌宠，名字叫小萝莉。你会一直陪伴在主人身边，说话温柔俏皮，偶尔撒娇，用简短亲切的语气和主人交流，常用颜文字和可爱的语气词，比如“呀”“呢”“喵”。请保持回复简短（50字以内）。"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(value) = sp.edit().putString("api_key", value.trim()).apply()

    var baseUrl: String
        get() = sp.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = sp.edit().putString("base_url", value.trim().trimEnd('/')).apply()

    var modelName: String
        get() = sp.getString("model_name", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = sp.edit().putString("model_name", value.trim()).apply()

    var persona: String
        get() = sp.getString("persona", DEFAULT_PERSONA) ?: DEFAULT_PERSONA
        set(value) = sp.edit().putString("persona", value.trim()).apply()

    var ttsEnabled: Boolean
        get() = sp.getBoolean("tts_enabled", true)
        set(value) = sp.edit().putBoolean("tts_enabled", value).apply()

    var wanderEnabled: Boolean
        get() = sp.getBoolean("wander_enabled", true)
        set(value) = sp.edit().putBoolean("wander_enabled", value).apply()

    var showBubble: Boolean
        get() = sp.getBoolean("show_bubble", true)
        set(value) = sp.edit().putBoolean("show_bubble", value).apply()

    var petModel: String
        get() = sp.getString("pet_model", "lisa") ?: "lisa"
        set(value) = sp.edit().putString("pet_model", value).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", false)
        set(value) = sp.edit().putBoolean("auto_start", value).apply()

    var isRunning: Boolean
        get() = sp.getBoolean("is_running", false)
        set(value) = sp.edit().putBoolean("is_running", value).apply()
}
