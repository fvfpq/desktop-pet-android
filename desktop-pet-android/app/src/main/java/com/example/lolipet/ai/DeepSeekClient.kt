package com.example.lolipet.ai

import com.example.lolipet.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** DeepSeek 对话 API 客户端（OpenAI 兼容协议） */
object DeepSeekClient {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ChatMessage(val role: String, val content: String)

    /** 单轮对话（无历史），用于测试连接 */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val reply = chat(listOf(ChatMessage("user", "请回复“在呢”两个字")))
            reply.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** 流式对话：回调逐段返回增量文本 */
    suspend fun chatStream(
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit
    ): String {
        return withContext(Dispatchers.IO) {
            val body = buildRequestBody(messages, stream = true)
            val request = Request.Builder()
                .url(Prefs.baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + Prefs.apiKey)
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build()

            val full = StringBuilder()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: ""
                    throw RuntimeException("HTTP ${resp.code}: $err")
                }
                val source = resp.body?.source() ?: throw RuntimeException("空响应")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:")) {
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val obj = JSONObject(data)
                            val delta = obj.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content", "")
                            if (!delta.isNullOrEmpty()) {
                                full.append(delta)
                                onDelta(delta)
                            }
                        } catch (e: Exception) {
                            // 忽略解析失败的行
                        }
                    }
                }
            }
            full.toString()
        }
    }

    /** 非流式对话 */
    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val body = buildRequestBody(messages, stream = false)
        val request = Request.Builder()
            .url(Prefs.baseUrl + "/chat/completions")
            .addHeader("Authorization", "Bearer " + Prefs.apiKey)
            .post(body)
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: ""
                throw RuntimeException("HTTP ${resp.code}: $err")
            }
            val text = resp.body?.string() ?: ""
            val obj = JSONObject(text)
            obj.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?: ""
        }
    }

    private fun buildRequestBody(messages: List<ChatMessage>, stream: Boolean): RequestBody {
        val arr = JSONArray()
        // 系统人设
        arr.put(JSONObject().put("role", "system").put("content", Prefs.persona))
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }

        val root = JSONObject()
            .put("model", Prefs.modelName)
            .put("messages", arr)
            .put("stream", stream)
            .put("temperature", 0.8)
            .put("max_tokens", 500)
        return root.toString().toRequestBody(json)
    }
}
