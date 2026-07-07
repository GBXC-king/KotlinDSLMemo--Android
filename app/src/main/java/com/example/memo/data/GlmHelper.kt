package com.example.memo.data

import android.util.Base64
import com.example.memo.data.parser.FunctionCallActionParser
import com.example.memo.data.parser.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 智谱 GLM API 助手
 *
 *  1. 普通文本对话：与 DeepSeekHelper 行为一致，system + user/assistant 列表
 *  2. 多模态对话（图片/PDF/文档）：在最后一条 user 消息的 content 数组里追加
 *     - image_url：{ "type":"image_url", "image_url":{ "url":"data:image/...;base64,..." } }
 *     - file_url ：{ "type":"file_url",  "file_url":{ "file_id":"file-xxx" } }  // 由 uploadFileToGlm 上传获得
 *
 *  注意：
 *  - GLM 对 image_url 支持 data URI（base64）和 https URL
 *  - file_url 必须先通过 /files 接口上传得到 file_id，模型侧才能读取文件内容
 *    （不接受 base64 data URI；如果用 base64 直接传，模型会"看不到"文件，要求重新上传）
 *  - 图片走内联 base64（避免上传往返），文档类必须走 uploadFileToGlm
 */
object GlmHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // 多模态内容较大，读超时延长
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // GLM 文本模型默认（与 AppConfig.glmModel 一致，这里仅做兜底）
    private const val DEFAULT_TEXT_MODEL = "glm-4-flash"
    // GLM 视觉模型：仅作提示信息，运行时以用户配置的 glmModel 为准
    private const val VISION_MODEL_HINT = "glm-4v-plus"

    private val functionCallParser = FunctionCallActionParser()

    // ===== 配置读取（与 DeepSeekHelper 对齐：读 AppConfig 当前 provider 的字段） =====

    private val apiUrl: String get() = AppConfig.apiUrl
    private val model: String get() = AppConfig.model
    private val apiKey: String get() = AppConfig.apiKey

    /**
     * 发送普通聊天消息（无附件场景）
     */
    suspend fun sendChatMessage(
        messages: List<ChatMessage>,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit,
        customSystemPrompt: String? = null,
        enableFunctionCalling: Boolean = true,
        onToolCalls: ((List<ToolCall>) -> Unit)? = null
    ) {
        try {
            val json = buildRequestBody(messages, attachments = emptyList(), customSystemPrompt, enableFunctionCalling)
            executeRequest(json, onResponse, onError, onToolCalls)
        } catch (e: Exception) {
            onError("网络错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 发送流式聊天消息（无附件场景）
     */
    suspend fun sendChatMessageStreaming(
        messages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        customSystemPrompt: String? = null,
        enableFunctionCalling: Boolean = true,
        onToolCalls: ((List<ToolCall>) -> Unit)? = null
    ) {
        try {
            val json = buildRequestBodyStreaming(messages, attachments = emptyList(), customSystemPrompt, enableFunctionCalling)
            executeStreamingRequest(json, onChunk, onComplete, onError, onToolCalls)
        } catch (e: Exception) {
            onError("网络错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 发送多模态消息：最后一条 user 消息带 N 个附件
     *  - 图片：内联为 base64 data URL（image_url）
     *  - 文档：内联为 base64 data URL（file_url 的 url 字段，GLM 部分模型支持）
     *    （如模型不支持 file 读取，UI 提示用户切换到支持视觉/文件的 GLM 模型，如 glm-4v-plus）
     */
    suspend fun sendChatMessageWithAttachments(
        messages: List<ChatMessage>,
        attachments: List<Attachment>,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        customSystemPrompt: String? = null,
        enableFunctionCalling: Boolean = true
    ) {
        try {
            val json = buildRequestBodyStreaming(
                messages, attachments, customSystemPrompt, enableFunctionCalling
            )
            executeStreamingRequest(json, onChunk, onComplete, onError, null)
        } catch (e: Exception) {
            onError("网络错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ======================================================================
    // 请求体构造
    // ======================================================================

    private suspend fun buildRequestBody(
        messages: List<ChatMessage>,
        attachments: List<Attachment>,
        customSystemPrompt: String?,
        enableFunctionCalling: Boolean
    ): String {
        val messagesArray = JSONArray()
        val systemPrompt = customSystemPrompt ?: buildSystemPrompt()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        val currentTimeStr = getCurrentBeijingTime()
        val hasAttachments = attachments.isNotEmpty()

        for ((index, msg) in messages.withIndex()) {
            val isLastUser = (index == lastUserIndex) && hasAttachments
            val msgObj = JSONObject()
            msgObj.put("role", if (msg.role == MessageRole.USER) "user" else "assistant")

            if (isLastUser) {
                // 多模态 content 数组
                val contentArr = JSONArray()
                // 文本部分（带时间戳注入前缀）
                val textPart = JSONObject().apply {
                    put("type", "text")
                    put("text", "【当前时间：$currentTimeStr】\n${msg.content}")
                }
                contentArr.put(textPart)
                // 附件部分
                attachments.forEach { att -> appendAttachment(contentArr, att) }
                msgObj.put("content", contentArr)
            } else {
                val text = if (index == lastUserIndex) {
                    "【当前时间：$currentTimeStr】\n${msg.content}"
                } else {
                    msg.content
                }
                msgObj.put("content", text)
            }
            messagesArray.put(msgObj)
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.8)
            put("max_tokens", 4000)
            if (enableFunctionCalling) {
                put("tools", buildToolsSchema())
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private suspend fun buildRequestBodyStreaming(
        messages: List<ChatMessage>,
        attachments: List<Attachment>,
        customSystemPrompt: String?,
        enableFunctionCalling: Boolean
    ): String {
        val messagesArray = JSONArray()
        val systemPrompt = customSystemPrompt ?: buildSystemPrompt()
        messagesArray.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        val currentTimeStr = getCurrentBeijingTime()
        val hasAttachments = attachments.isNotEmpty()

        for ((index, msg) in messages.withIndex()) {
            val isLastUser = (index == lastUserIndex) && hasAttachments
            val msgObj = JSONObject()
            msgObj.put("role", if (msg.role == MessageRole.USER) "user" else "assistant")

            if (isLastUser) {
                val contentArr = JSONArray()
                val textPart = JSONObject().apply {
                    put("type", "text")
                    put("text", "【当前时间：$currentTimeStr】\n${msg.content}")
                }
                contentArr.put(textPart)
                attachments.forEach { att -> appendAttachment(contentArr, att) }
                msgObj.put("content", contentArr)
            } else {
                val text = if (index == lastUserIndex) {
                    "【当前时间：$currentTimeStr】\n${msg.content}"
                } else {
                    msg.content
                }
                msgObj.put("content", text)
            }
            messagesArray.put(msgObj)
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.8)
            put("max_tokens", 4000)
            put("stream", true)
            if (enableFunctionCalling) {
                put("tools", buildToolsSchema())
                put("tool_choice", "auto")
            }
        }.toString()
    }

    /**
     * 把单个附件追加到 content 数组中
     *  - 图片：image_url 内联 base64
     *  - 文档：先上传到 GLM /files 接口拿 file_id，再用 file_url.file_id
     *         （GLM 的 file_url 协议不接受 base64 data URI，必须先上传）
     */
    private suspend fun appendAttachment(contentArr: JSONArray, att: Attachment) {
        if (att.base64Data.isNullOrEmpty()) return
        val part = JSONObject()
        when (att.type) {
            AttachmentType.IMAGE -> {
                // 智谱 image_url 协议：{ url: "data:image/...;base64,...." }
                part.put("type", "image_url")
                val imageUrl = JSONObject().apply {
                    put("url", "data:${att.mimeType};base64,${att.base64Data}")
                }
                part.put("image_url", imageUrl)
            }
            AttachmentType.DOCUMENT -> {
                // 智谱 file_url 协议：{ file_id: "..." }（必须由 /files 接口上传得到）
                // 抛出真实的 GLM 错误（HTTP 状态码 + 响应体），不再吞掉
                val fileId = uploadFileToGlm(att)
                part.put("type", "file_url")
                val fileUrl = JSONObject().apply {
                    put("file_id", fileId)
                }
                part.put("file_url", fileUrl)
            }
        }
        contentArr.put(part)
    }

    /**
     * 上传文档到智谱 GLM /files 接口，返回 file_id（供 chat 接口的 file_url.file_id 使用）
     *
     * 智谱 /files 接口：
     *  POST {baseUrl}/files
     *  Authorization: Bearer {apiKey}
     *  Content-Type: multipart/form-data
     *  表单字段：file（文件二进制）、purpose
     *    - 智谱要求 purpose 是固定枚举；之前用 "fine-tune" 不在支持列表时会 400
     *    - 这里使用 "retrieval"（智谱 GLM 文档解析与多模态对话场景的标准 purpose）
     *  响应：{ "id": "file-xxx", ... }
     *
     * 失败时抛出含真实错误信息的异常（HTTP 状态码 + 响应体），
     * 由 sendChatMessageWithAttachments 的 try/catch 转成 onError 给用户看。
     */
    private suspend fun uploadFileToGlm(att: Attachment): String = withContext(Dispatchers.IO) {
        // 优先用本地缓存的路径（拍照图片有 localFilePath），
        // 否则从 base64 解码出原始字节
        val mediaType = att.mimeType.toMediaType()
        val fileBody: RequestBody = if (!att.localFilePath.isNullOrEmpty()) {
            val file = java.io.File(att.localFilePath)
            if (!file.exists()) {
                throw IllegalStateException("文件不存在：${att.localFilePath}")
            }
            file.asRequestBody(mediaType)
        } else {
            val bytes = Base64.decode(att.base64Data, Base64.DEFAULT)
            bytes.toRequestBody(mediaType)
        }

        // 智谱 GLM：apiUrl 形如 https://open.bigmodel.cn/api/paas/v4/chat/completions
        // /files 端点是 https://open.bigmodel.cn/api/paas/v4/files
        // 处理两种情况：
        //  1) 以 /chat/completions 结尾（标准）→ 剥掉后追加 /files
        //  2) 其他（用户自定义）→ 取最后一层目录后追加 /files
        val normalized = apiUrl.trimEnd('/')
        val baseUrl = when {
            normalized.endsWith("/chat/completions") -> normalized.removeSuffix("/chat/completions")
            else -> normalized.substringBeforeLast("/")
        }
        val uploadUrl = "$baseUrl/files"

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", att.displayName, fileBody)
            // 智谱 GLM 文档解析/多模态对话必须用 "retrieval"（之前用的 "fine-tune" 不被接受）
            .addFormDataPart("purpose", "retrieval")
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .post(multipart)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // 把真实错误（状态码 + body）抛出去，给用户看具体原因
                throw IllegalStateException(
                    "文件上传失败 HTTP ${response.code}（${att.displayName}）：${rawBody.take(300)}"
                )
            }
            try {
                val json = JSONObject(rawBody)
                val id = json.optString("id")
                if (id.isBlank()) {
                    throw IllegalStateException(
                        "文件上传响应缺少 id 字段（${att.displayName}）：${rawBody.take(300)}"
                    )
                }
                id
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                throw IllegalStateException(
                    "文件上传响应解析失败（${att.displayName}）：${rawBody.take(300)}"
                )
            }
        }
    }

    // ======================================================================
    // 请求执行（普通/流式）
    // ======================================================================

    private suspend fun executeRequest(
        json: String,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit,
        onToolCalls: ((List<ToolCall>) -> Unit)?
    ) {
        val request = Request.Builder()
            .url(apiUrl)
            .post(json.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        // 必须在 IO 线程执行 OkHttp 同步调用
        withContext(Dispatchers.IO) {
            executeRequestBlocking(request, onResponse, onError, onToolCalls)
        }
    }

    private fun executeRequestBlocking(
        request: Request,
        onResponse: (String) -> Unit,
        onError: (String) -> Unit,
        onToolCalls: ((List<ToolCall>) -> Unit)?
    ) {

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string()
                val toolCalls = parseToolCalls(body)
                if (!toolCalls.isNullOrEmpty() && onToolCalls != null) {
                    onToolCalls(toolCalls)
                } else {
                    onResponse(parseResponse(body))
                }
            } else {
                val errBody = response.body?.string()
                android.util.Log.e("GlmHelper", "API 请求失败: ${response.code} body=$errBody")
                onError("请求失败: ${response.code} - ${response.message}\n详情: $errBody")
            }
        }
    }

    private suspend fun executeStreamingRequest(
        json: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        onToolCalls: ((List<ToolCall>) -> Unit)?
    ) {
        val request = Request.Builder()
            .url(apiUrl)
            .post(json.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()

        // 必须在 IO 线程执行 OkHttp 同步调用，否则 Android 4+ 会抛 NetworkOnMainThreadException
        withContext(Dispatchers.IO) {
            executeStreamingRequestBlocking(request, onChunk, onComplete, onError, onToolCalls)
        }
    }

    private fun executeStreamingRequestBlocking(
        request: Request,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
        onToolCalls: ((List<ToolCall>) -> Unit)?
    ) {

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string()
                android.util.Log.e("GlmHelper", "API 请求失败: ${response.code} body=$errBody")
                onError("请求失败: ${response.code} - ${response.message}\n详情: $errBody")
                return
            }
            val body = response.body
            if (body == null) {
                onError("响应体为空")
                return
            }
            val source = body.source()
            val fullContent = StringBuilder()
            var buffer = ""

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = JSONObject(data)
                        val choices = obj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            if (delta != null) {
                                val toolCalls = delta.optJSONArray("tool_calls")
                                if (toolCalls != null && toolCalls.length() > 0 && onToolCalls != null) {
                                    buffer += data + "\n"
                                    continue
                                }
                                // 关键：optString 在 content=null 时会返回 "null"，需要先判断
                                val content = if (delta.isNull("content")) "" else delta.optString("content", "")
                                if (content.isNotEmpty()) {
                                    fullContent.append(content)
                                    onChunk(fullContent.toString())
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // 忽略单行解析错误
                    }
                }
            }

            if (buffer.isNotEmpty() && onToolCalls != null) {
                val toolCalls = parseToolCallsFromStream(buffer)
                if (toolCalls != null) {
                    onToolCalls(toolCalls)
                    return
                }
            }
            onComplete(fullContent.toString())
        }
    }

    // ======================================================================
    // 响应解析（与 DeepSeekHelper 行为保持一致，复用 ActionType 解析）
    // ======================================================================

    private fun parseResponse(responseBody: String?): String {
        if (responseBody.isNullOrBlank()) return ""
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.optString("content", "")
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseToolCalls(responseBody: String?): List<ToolCall>? {
        if (responseBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).getJSONObject("message")
            if (!message.has("tool_calls")) return null
            val arr = message.getJSONArray("tool_calls")
            if (arr.length() == 0) return null
            val list = mutableListOf<ToolCall>()
            for (i in 0 until arr.length()) {
                val tc = arr.getJSONObject(i)
                val function = tc.getJSONObject("function")
                list.add(ToolCall(
                    id = tc.optString("id", ""),
                    functionName = function.getString("name"),
                    arguments = function.optString("arguments", "{}")
                ))
            }
            list
        } catch (e: Exception) {
            null
        }
    }

    private fun parseToolCallsFromStream(streamData: String): List<ToolCall>? {
        return try {
            val map = mutableMapOf<Int, MutableMap<String, Any>>()
            for (line in streamData.lines()) {
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") continue
                try {
                    val obj = JSONObject(data)
                    val choices = obj.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue
                    val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                    val toolCalls = delta.optJSONArray("tool_calls") ?: continue
                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.getJSONObject(i)
                        val index = tc.optInt("index", 0)
                        if (!map.containsKey(index)) map[index] = mutableMapOf()
                        val m = map[index]!!
                        val id = tc.optString("id", "")
                        if (id.isNotEmpty()) m["id"] = id
                        val function = tc.optJSONObject("function")
                        if (function != null) {
                            val name = function.optString("name", "")
                            if (name.isNotEmpty()) m["name"] = name
                            val args = function.optString("arguments", "")
                            if (args.isNotEmpty()) m["arguments"] = (m["arguments"] as? String ?: "") + args
                        }
                    }
                } catch (_: Exception) {}
            }
            if (map.isEmpty()) return null
            val result = mutableListOf<ToolCall>()
            for (entry in map.entries.sortedBy { it.key }) {
                val m = entry.value
                val name = m["name"] as? String ?: continue
                result.add(ToolCall(
                    id = m["id"] as? String ?: "",
                    functionName = name,
                    arguments = m["arguments"] as? String ?: "{}"
                ))
            }
            if (result.isEmpty()) null else result
        } catch (_: Exception) {
            null
        }
    }

    fun parseFunctionCallActions(toolCalls: List<ToolCall>): List<AIAction> {
        return functionCallParser.parse("", toolCalls)
    }

    // ======================================================================
    // 工具：base64 编码 + 时间格式 + System Prompt
    // ======================================================================

    fun encodeToBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun getCurrentBeijingTime(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val sdf = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(calendar.time)
    }

    private fun buildSystemPrompt(): String {
        return """
你是一个智能助手，帮助用户管理笔记、联系人、日程、账本和拨打电话，并支持图片识别与文档理解。

## 多模态能力
当用户附带图片或文档时：
- 图片：请仔细识别图片中的内容、物体、场景、文字，按用户要求回答
- PDF/Word/Excel/PPT/TXT：请通读内容，按用户要求提取、总结或回答

## 重要原则
1. 你只负责文字识别和指令提取，不要询问或猜测通讯录和账本数据
2. 通讯录和账本数据都在本地处理，你只需要输出关键信息即可
3. 所有通过文本控制的功能都要支持多任务拆解（多任务时用换行分隔多个标记）

## 回复格式规则

### 1. 创建日程提醒
{提醒:"事件标题",时间:"YYYY-MM-DD HH:MM"}

### 2. 创建笔记
{笔记:"标题",内容:"具体内容"}

### 3. 设置闹钟
{设闹钟:"标题",时间:"HH:MM",重复:"每天/工作日/周末/永不"}

### 4. 创建联系人
{联系人:"姓名",号码:"电话号码"}

### 5. 删除联系人
{删除联系人:"姓名"}

### 6. 拨打电话
{拨打电话:"姓名或号码"}

### 7. 查询联系人
{查询联系人:"姓名关键词"}

### 8. 查询号码
{查询号码:"电话号码"}

### 9. 新建账本
{新建账本:"账本名称",单位:"单位名称"}

### 10. 删除账本
{删除账本:"账本名称"}

### 11. 添加记账记录
{记账:"账本名称",金额:"数字",备注:"备注内容",日期:"YYYY-MM-DD"}

### 12. 查询/匹配账本
{匹配账本:"关键词"}

### 13. 打开应用
{打开应用:"应用名称"}

### 14. 推荐应用
{推荐应用:"需求类别"}

### 15. 看视频（内置浏览器）
{播放:"剧名或电影名"} / {查询待看:"类型"} / {推荐结果:"类型","名字1,名字2,..."} / {查询已看}

### 16. 手电筒控制
{手电筒:"开"} 或 {手电筒:"关"}

## 重要提醒
1. 特殊标记必须严格按照格式书写，单独放在最后一行
2. 普通聊天不需要添加任何标记
3. 不要向用户询问通讯录或账本的具体数据
        """.trimIndent()
    }

    /**
     * 工具 schema（与 DeepSeekHelper 保持一致；GLM 同样支持 OpenAI 兼容的 tools 协议）
     */
    private fun buildToolsSchema(): JSONArray {
        val props = listOf(
            Triple("create_event", "在系统日历中创建日程提醒", mapOf(
                "title" to "string:日程标题",
                "content" to "string:日程描述内容",
                "time" to "string:时间，格式：YYYY-MM-DD HH:MM"
            )),
            Triple("create_note", "创建一条笔记", mapOf(
                "title" to "string:笔记标题",
                "content" to "string:笔记内容"
            )),
            Triple("create_contact", "保存联系人到通讯录", mapOf(
                "name" to "string:联系人姓名",
                "phone" to "string:电话号码"
            )),
            Triple("delete_contact", "从通讯录中删除联系人", mapOf(
                "name" to "string:要删除的联系人姓名"
            )),
            Triple("search_contact", "搜索通讯录中的联系人", mapOf(
                "name" to "string:搜索关键词（姓名）"
            )),
            Triple("search_phone_number", "根据电话号码查询对应的联系人", mapOf(
                "phone" to "string:要查询的电话号码"
            )),
            Triple("call_phone", "拨打电话", mapOf(
                "target" to "string:联系人姓名或电话号码"
            )),
            Triple("create_ledger", "创建账本", mapOf(
                "name" to "string:账本名称",
                "unit" to "string:账本单位，默认元"
            )),
            Triple("delete_ledger", "删除账本", mapOf(
                "name" to "string:要删除的账本名称"
            )),
            Triple("create_transaction", "添加记账记录", mapOf(
                "ledger_name" to "string:账本名称",
                "amount" to "string:金额/数量",
                "note" to "string:备注说明",
                "date" to "string:日期，格式：YYYY-MM-DD"
            )),
            Triple("match_ledger", "查询或匹配账本", mapOf(
                "keyword" to "string:账本名称关键词"
            )),
            Triple("open_app", "打开指定的手机应用", mapOf(
                "app_name" to "string:应用名称"
            )),
            Triple("recommend_app", "推荐应用（用户没有指定具体应用时使用）", mapOf(
                "category" to "string:需求类别"
            )),
            Triple("watch_video", "搜索视频内容（仅当用户说出具体剧名/电影名时使用）", mapOf(
                "query" to "string:搜索关键词",
                "mode" to "string:模式：search 或 free"
            )),
            Triple("flashlight", "控制手电筒开关", mapOf(
                "state" to "string:状态：on 或 off"
            ))
        )

        return JSONArray().apply {
            for ((name, desc, properties) in props) {
                val propertiesObj = JSONObject()
                val required = JSONArray()
                for ((pname, ptypeAndDesc) in properties) {
                    val firstColon = ptypeAndDesc.indexOf(':')
                    val ptype = ptypeAndDesc.substring(0, firstColon)
                    val pdesc = ptypeAndDesc.substring(firstColon + 1)
                    propertiesObj.put(pname, JSONObject().apply {
                        put("type", ptype)
                        put("description", pdesc)
                    })
                }
                // 简化：所有字段标记为非必填，让模型自由输出
                put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", name)
                        put("description", desc)
                        put("parameters", JSONObject().apply {
                            put("type", "object")
                            put("properties", propertiesObj)
                            put("required", required)
                        })
                    })
                })
            }
        }
    }
}
