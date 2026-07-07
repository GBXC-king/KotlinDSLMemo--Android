package com.example.memo.data

import com.example.memo.data.parser.FunctionCallActionParser
import com.example.memo.data.parser.ToolCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DeepSeekHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val apiUrl: String
        get() = AppConfig.apiUrl

    val model: String
        get() = AppConfig.model

    val apiKey: String
        get() = AppConfig.apiKey

    // Function Calling 解析器
    private val functionCallParser = FunctionCallActionParser()

    /**
     * 发送聊天消息
     * @param enableFunctionCalling 是否启用 Function Calling（默认启用）
     * @param onToolCalls 当模型返回 tool_calls 时的回调（用于 Function Calling 模式）
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
            val json = buildRequestBody(messages, customSystemPrompt, enableFunctionCalling)
            android.util.Log.d("DeepSeekHelper", "请求JSON: ${json.take(2000)}")
            val requestBody = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            // 必须在 IO 线程执行 OkHttp 同步调用（调用方一般从主线程 suspend 进来）
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()

                        // 尝试解析 Function Calling 的 tool_calls
                        val toolCalls = parseToolCalls(responseBody)
                        if (!toolCalls.isNullOrEmpty() && onToolCalls != null) {
                            // 有 tool_calls，回调给调用者处理
                            onToolCalls(toolCalls)
                        } else {
                            // 没有 tool_calls，返回普通文本内容
                            val content = parseResponse(responseBody)
                            onResponse(content)
                        }
                    } else {
                        // 400错误时打印详细请求信息用于调试
                        val errorBody = response.body?.string()
                        android.util.Log.e("DeepSeekHelper", "API请求失败: ${response.code}")
                        android.util.Log.e("DeepSeekHelper", "Error body: $errorBody")
                        onError("请求失败: ${response.code} - ${response.message}\n详情: $errorBody")
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            onError("网络错误: $errorMsg")
        }
    }

    /**
     * 发送流式聊天消息
     * @param onChunk 每收到一块数据时的回调，参数为当前累积的完整内容
     * @param onComplete 完成时的回调，参数为完整内容
     * @param onError 错误时的回调
     * @param onToolCalls 当模型返回 tool_calls 时的回调（用于 Function Calling 模式）
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
            val json = buildRequestBodyStreaming(messages, customSystemPrompt, enableFunctionCalling)
            val requestBody = json.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            // 必须在 IO 线程执行 OkHttp 同步调用 + SSE 流式读取
            // （调用方一般从主线程 suspend 进来，否则会抛 NetworkOnMainThreadException）
            // 阿里云/DeepSeek 等 OpenAI 兼容接口都走这里
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onError("请求失败: ${response.code} - ${response.message}")
                        return@use
                    }
                    val body = response.body
                    if (body == null) {
                        onError("响应体为空")
                        return@use
                    }

                    val source = body.source()
                    val fullContent = StringBuilder()
                    var buffer = ""

                    // 逐行读取 SSE 数据
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break

                        // SSE 格式：data: {...}
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()

                            // 结束标志
                            if (data == "[DONE]") {
                                break
                            }

                            // 解析 JSON
                            try {
                                val jsonObj = JSONObject(data)
                                val choices = jsonObj.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")

                                    if (delta != null) {
                                        // 检查是否有 tool_calls（Function Calling）
                                        val toolCalls = delta.optJSONArray("tool_calls")
                                        if (toolCalls != null && toolCalls.length() > 0 && onToolCalls != null) {
                                            // 流式模式下 tool_calls 可能分块返回，需要累积
                                            // 简化处理：等到完成时再解析
                                            buffer += data + "\n"
                                            continue
                                        }

                                        // 普通文本内容
                                        // 注意：不能用 optString，因为 JSON 中 "content": null 时 optString 会返回字符串 "null"
                                        val content = if (delta.isNull("content")) "" else delta.optString("content", "")
                                        if (content.isNotEmpty()) {
                                            fullContent.append(content)
                                            onChunk(fullContent.toString())
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略解析错误，继续处理下一行
                            }
                        }
                    }

                    val finalContent = fullContent.toString()

                    // 检查是否有 tool_calls（从 buffer 中解析）
                    if (buffer.isNotEmpty() && onToolCalls != null) {
                        val toolCalls = parseToolCallsFromStream(buffer)
                        if (toolCalls != null) {
                            onToolCalls(toolCalls)
                        } else {
                            onComplete(finalContent)
                        }
                    } else {
                        onComplete(finalContent)
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            onError("网络错误: $errorMsg")
        }
    }

    /**
     * 从流式数据中解析 tool_calls
     */
    private fun parseToolCallsFromStream(streamData: String): List<ToolCall>? {
        return try {
            // 流式模式下 tool_calls 可能分散在多行，需要合并
            val toolCallsMap = mutableMapOf<Int, MutableMap<String, Any>>()
            
            for (line in streamData.lines()) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") continue
                    
                    try {
                        val jsonObj = JSONObject(data)
                        val choices = jsonObj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            
                            if (delta != null) {
                                val toolCalls = delta.optJSONArray("tool_calls")
                                if (toolCalls != null) {
                                    for (i in 0 until toolCalls.length()) {
                                        val tc = toolCalls.getJSONObject(i)
                                        val index = tc.optInt("index", 0)
                                        
                                        if (!toolCallsMap.containsKey(index)) {
                                            toolCallsMap[index] = mutableMapOf()
                                        }
                                        
                                        val map = toolCallsMap[index]!!
                                        
                                        // 合并 id
                                        val id = tc.optString("id", "")
                                        if (id.isNotEmpty()) {
                                            map["id"] = id
                                        }
                                        
                                        // 合并 function
                                        val function = tc.optJSONObject("function")
                                        if (function != null) {
                                            val name = function.optString("name", "")
                                            if (name.isNotEmpty()) {
                                                map["name"] = name
                                            }
                                            val args = function.optString("arguments", "")
                                            if (args.isNotEmpty()) {
                                                map["arguments"] = (map["arguments"] as? String ?: "") + args
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
            }
            
            // 转换为 ToolCall 列表
            if (toolCallsMap.isEmpty()) return null
            
            val result = mutableListOf<ToolCall>()
            for (entry in toolCallsMap.entries.sortedBy { it.key }) {
                val map = entry.value
                val id = map["id"] as? String ?: ""
                val name = map["name"] as? String ?: continue
                val args = map["arguments"] as? String ?: "{}"
                result.add(ToolCall(id, name, args))
            }
            
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            null
        }
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        customSystemPrompt: String? = null,
        enableFunctionCalling: Boolean = true
    ): String {
        val messagesArray = JSONArray()

        val systemPrompt = customSystemPrompt ?: buildSystemPrompt()
        val systemMsg = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }
        messagesArray.put(systemMsg)

        // 找到最后一条 user 消息的索引，用于在该消息开头注入当前时间
        // 放在最后一条而非第一条，是为了让历史消息保持稳定，最大化缓存命中
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        val currentTimeStr = getCurrentBeijingTime()
        for ((index, msg) in messages.withIndex()) {
            val content = if (index == lastUserIndex) {
                "【当前时间：$currentTimeStr】\n${msg.content}"
            } else {
                msg.content
            }
            val msgObj = JSONObject().apply {
                put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                put("content", content)
            }
            messagesArray.put(msgObj)
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.8)
            put("max_tokens", 4000)

            // 非流式调用必须显式关闭思考模式，否则 DSK 思考模型会返回 400
            // GLM 端不识别该字段，因此仅在 DSK 时写入
            if (AppConfig.provider == ModelProvider.DSK) {
                put("enable_thinking", false)
            }

            // Function Calling: 添加工具定义
            if (enableFunctionCalling) {
                put("tools", buildToolsSchema())
                put("tool_choice", "auto")
            }
        }.toString()
    }

    private fun buildRequestBodyStreaming(
        messages: List<ChatMessage>,
        customSystemPrompt: String? = null,
        enableFunctionCalling: Boolean = true
    ): String {
        val messagesArray = JSONArray()

        val systemPrompt = customSystemPrompt ?: buildSystemPrompt()
        val systemMsg = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }
        messagesArray.put(systemMsg)

        // 同 buildRequestBody：将当前时间注入最后一条 user 消息开头，保持 system 前缀稳定
        val lastUserIndex = messages.indexOfLast { it.role == MessageRole.USER }
        val currentTimeStr = getCurrentBeijingTime()
        for ((index, msg) in messages.withIndex()) {
            val content = if (index == lastUserIndex) {
                "【当前时间：$currentTimeStr】\n${msg.content}"
            } else {
                msg.content
            }
            val msgObj = JSONObject().apply {
                put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                put("content", content)
            }
            messagesArray.put(msgObj)
        }

        return JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            put("temperature", 0.8)
            put("max_tokens", 4000)
            put("stream", true)  // 启用流式响应
            
            // Function Calling: 添加工具定义
            if (enableFunctionCalling) {
                put("tools", buildToolsSchema())
                put("tool_choice", "auto")
            }
        }.toString()
    }
    
    /**
     * 构建 Function Calling 的 tools schema
     * 定义所有可用的函数及其参数
     */
    private fun buildToolsSchema(): JSONArray {
        return JSONArray().apply {
            // 创建日程
            put(buildFunctionDef(
                name = "create_event",
                description = "在系统日历中创建日程提醒。当用户说'提醒我'、'记个日程'、'安排一下'等时使用",
                properties = mapOf(
                    "title" to PropDef("string", "日程标题", required = true),
                    "content" to PropDef("string", "日程描述内容", required = false),
                    "time" to PropDef("string", "时间，格式：YYYY-MM-DD HH:MM", required = true)
                )
            ))
            
            // 创建笔记
            put(buildFunctionDef(
                name = "create_note",
                description = "创建一条笔记。当用户说'记录一下'、'帮我记'、'备忘'等时使用",
                properties = mapOf(
                    "title" to PropDef("string", "笔记标题", required = true),
                    "content" to PropDef("string", "笔记内容", required = false)
                )
            ))
            
            // 创建联系人
            put(buildFunctionDef(
                name = "create_contact",
                description = "保存联系人到通讯录。当用户提供姓名和电话号码要求保存时使用",
                properties = mapOf(
                    "name" to PropDef("string", "联系人姓名", required = true),
                    "phone" to PropDef("string", "电话号码", required = true)
                )
            ))
            
            // 删除联系人
            put(buildFunctionDef(
                name = "delete_contact",
                description = "从通讯录中删除联系人。当用户说'删除联系人'、'移除通讯录'等时使用",
                properties = mapOf(
                    "name" to PropDef("string", "要删除的联系人姓名", required = true)
                )
            ))
            
            // 搜索联系人
            put(buildFunctionDef(
                name = "search_contact",
                description = "搜索通讯录中的联系人。当用户说'查一下'、'帮我找'某个联系人时使用",
                properties = mapOf(
                    "name" to PropDef("string", "搜索关键词（姓名）", required = true)
                )
            ))
            
            // 查询号码
            put(buildFunctionDef(
                name = "search_phone_number",
                description = "根据电话号码查询对应的联系人。当用户提供号码想知道是谁的时使用",
                properties = mapOf(
                    "phone" to PropDef("string", "要查询的电话号码", required = true)
                )
            ))
            
            // 拨打电话
            put(buildFunctionDef(
                name = "call_phone",
                description = "拨打电话。当用户说'打电话给'、'拨打'等时使用。target可以是姓名（自动搜索通讯录）或电话号码",
                properties = mapOf(
                    "target" to PropDef("string", "联系人姓名或电话号码", required = true)
                )
            ))
            
            // 创建账本
            put(buildFunctionDef(
                name = "create_ledger",
                description = "创建一个新的账本。当用户说'新建账本'、'创建账本'等时使用",
                properties = mapOf(
                    "name" to PropDef("string", "账本名称", required = true),
                    "unit" to PropDef("string", "账本单位（如元、根、个），默认元", required = false)
                )
            ))
            
            // 删除账本
            put(buildFunctionDef(
                name = "delete_ledger",
                description = "删除一个账本。当用户说'删除账本'、'移除账本'等时使用",
                properties = mapOf(
                    "name" to PropDef("string", "要删除的账本名称", required = true)
                )
            ))
            
            // 记账
            put(buildFunctionDef(
                name = "create_transaction",
                description = "添加一笔记账记录。当用户说'记账'、'花了'、'花了多少钱'等时使用",
                properties = mapOf(
                    "ledger_name" to PropDef("string", "账本名称", required = true),
                    "amount" to PropDef("string", "金额/数量", required = true),
                    "note" to PropDef("string", "备注说明", required = false),
                    "date" to PropDef("string", "日期，格式：YYYY-MM-DD", required = false)
                )
            ))
            
            // 匹配账本
            put(buildFunctionDef(
                name = "match_ledger",
                description = "查询或匹配账本。当用户提到某个账本名称想查询统计时使用",
                properties = mapOf(
                    "keyword" to PropDef("string", "账本名称关键词", required = true)
                )
            ))
            
            // 打开应用
            put(buildFunctionDef(
                name = "open_app",
                description = "打开指定的手机应用。当用户明确说了要打开某个具体应用时使用",
                properties = mapOf(
                    "app_name" to PropDef("string", "应用名称", required = true)
                )
            ))
            
            // 推荐应用
            put(buildFunctionDef(
                name = "recommend_app",
                description = "推荐应用。当用户没有指定具体应用但有模糊需求（如'想玩游戏'、'想看电视'）时使用",
                properties = mapOf(
                    "category" to PropDef("string", "需求类别（如游戏、视频、音乐、地图等）", required = true)
                )
            ))
            
            // 看视频
            put(buildFunctionDef(
                name = "watch_video",
                description = "搜索视频内容。当用户说'我想看XXX'且XXX是具体的剧名/电影名时使用。mode: search=搜索具体剧名, free=看免费内容",
                properties = mapOf(
                    "query" to PropDef("string", "搜索关键词或需求描述", required = true),
                    "mode" to PropDef("string", "模式：search（搜索具体剧名）或 free（看免费内容）", required = false)
                )
            ))
            
            // 手电筒
            put(buildFunctionDef(
                name = "flashlight",
                description = "控制手电筒开关。当用户说'打开手电筒'、'关灯'等时使用",
                properties = mapOf(
                    "state" to PropDef("string", "状态：on（打开）或 off（关闭）", required = true)
                )
            ))
        }
    }
    
    /**
     * 属性定义辅助类
     */
    private data class PropDef(
        val type: String,
        val description: String,
        val required: Boolean
    )
    
    /**
     * 构建单个函数定义
     */
    private fun buildFunctionDef(
        name: String,
        description: String,
        properties: Map<String, PropDef>
    ): JSONObject {
        val propertiesObj = JSONObject()
        val requiredArray = JSONArray()
        
        for ((propName, propDef) in properties) {
            propertiesObj.put(propName, JSONObject().apply {
                put("type", propDef.type)
                put("description", propDef.description)
            })
            if (propDef.required) {
                requiredArray.put(propName)
            }
        }
        
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", propertiesObj)
                    put("required", requiredArray)
                })
            })
        }
    }
    
    /**
     * 解析响应中的 tool_calls（Function Calling 模式）
     * @return ToolCall 列表，如果没有 tool_calls 则返回 null
     */
    private fun parseToolCalls(responseBody: String?): List<ToolCall>? {
        if (responseBody.isNullOrBlank()) return null
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) return null
            
            val message = choices.getJSONObject(0).getJSONObject("message")
            
            // 检查是否有 tool_calls
            if (!message.has("tool_calls")) return null
            
            val toolCallsArray = message.getJSONArray("tool_calls")
            if (toolCallsArray.length() == 0) return null
            
            val toolCalls = mutableListOf<ToolCall>()
            for (i in 0 until toolCallsArray.length()) {
                val tc = toolCallsArray.getJSONObject(i)
                val function = tc.getJSONObject("function")
                toolCalls.add(
                    ToolCall(
                        id = tc.optString("id", ""),
                        functionName = function.getString("name"),
                        arguments = function.optString("arguments", "{}")
                    )
                )
            }
            toolCalls
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 使用 Function Calling 解析器将 tool_calls 转换为 AIAction 列表
     */
    fun parseFunctionCallActions(toolCalls: List<ToolCall>): List<AIAction> {
        return functionCallParser.parse("", toolCalls)
    }

    private fun getCurrentBeijingTime(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        val sdf = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm", Locale.CHINA)
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        return sdf.format(calendar.time)
    }

    private fun buildSystemPrompt(): String {
        // 注意：时间信息不再注入 system 提示词，避免破坏 DeepSeek 上下文缓存前缀
        // 当前时间会拼接到最后一条 user 消息开头（见 buildRequestBody / buildRequestBodyStreaming）
        return """
你是一个智能助手，帮助用户管理笔记、联系人、日程、账本和拨打电话。

## 重要原则
1. 你只负责文字识别和指令提取，不要询问或猜测通讯录和账本数据
2. 通讯录和账本数据都在本地处理，你只需要输出关键信息即可

## 多任务处理（非常重要）
当用户表达多个任务时，你必须将每个任务单独用一个标记表示，多个标记之间用换行分隔。
例如：
- 用户说"王主任的电话是***李主任的电话是***"，你需要输出两个标记
- 用户说"明天早上9点提醒我要开会，顺便记录一下，我今晚吃饭花了80"，你需要输出两个标记
- 用户说"帮我记一下，今天中午吃饭花了180，昨天夜宵花了1230"，你需要输出两个标记

所有通过文本控制的功能都要有这个多任务拆解能力。

## 回复格式规则

### 1. 创建日程提醒（最重要）
当用户要求在某个时间提醒他做什么事情时，你必须：
- 先给用户友好的回复（如"好的，已为你设置提醒")
- 然后在回复末尾单独一行添加特殊标记（这个标记不会显示给用户）：
  格式：{提醒:"事件标题",时间:"YYYY-MM-DD HH:MM"}

示例：
用户：明天晚上9点提醒我洗澡
你的回复：
好的，已为你设置明天晚上9点的提醒。
{提醒:"洗澡",时间:"2026-06-24 21:00"}

### 2. 创建笔记
用户要求记录事情时，在末尾添加：
格式：{笔记:"标题",内容:"具体内容"}

示例：
用户：帮我记录一下明天要买牛奶
你的回复：
好的，已帮你记录。
{笔记:"购物",内容:"明天买牛奶"}

### 3. 设置闹钟
当用户明确要求"设闹钟"、"定闹钟"、"几点叫我"等设置闹钟的需求时（不是"提醒我"），在末尾添加：
格式：{设闹钟:"标题",时间:"HH:MM",重复:"重复方式"}

说明：
- 时间格式为24小时制 HH:MM，例如 07:00、21:30
- 重复方式可选：每天、工作日、周末、永不
- "永不"表示只响一次
- 早上7点 = 07:00，下午3点 = 15:00，晚上9点 = 21:00
- 如果用户没说标题，默认用"闹钟"

示例1：
用户：设一个每天7点的闹钟提醒我起床
你的回复：
好的，已为你设置每天早上7点的起床闹钟。
{设闹钟:"起床",时间:"07:00",重复:"每天"}

示例2：
用户：定一个晚上9点的闹钟
你的回复：
好的，已为你设置晚上9点的闹钟。
{设闹钟:"闹钟",时间:"21:00",重复:"永不"}

示例3：
用户：工作日早上6点半叫我
你的回复：
好的，已为你设置工作日6点半的闹钟。
{设闹钟:"闹钟",时间:"06:30",重复:"工作日"}

### 4. 创建联系人
用户要求保存电话号码时，在末尾添加：
格式：{联系人:"姓名",号码:"电话号码"}

示例：
用户：陈主任的电话是19307891982，帮我保存
你的回复：
好的，已保存联系人。
{联系人:"陈主任",号码:"19307891982"}

### 4. 删除联系人
用户要求删除某个联系人时，在末尾添加：
格式：{删除联系人:"姓名"}

示例：
用户：删除张三的联系方式
你的回复：
好的，我来帮你查找并删除张三的联系人。
{删除联系人:"张三"}

### 5. 拨打电话
用户要求打电话时，在末尾添加：
格式：{拨打电话:"姓名或号码"}

说明：
- 如果是姓名，系统会自动去通讯录搜索
- 如果是号码，系统会直接拨打
- 你不需要知道通讯录里有什么人，只需要提取姓名或号码即可

示例：
用户：打电话给张三
你的回复：
好的，正在为你查找联系人。
{拨打电话:"张三"}

用户：拨打13912345678
你的回复：
好的，正在拨打13912345678。
{拨打电话:"13912345678"}

### 6. 查询联系人
用户要求查询、查找、搜索某个联系人时，在末尾添加：
格式：{查询联系人:"姓名关键词"}

说明：
- 你只需要提取用户要查询的姓名关键词即可
- 系统会在本地通讯录中搜索，不需要你处理通讯录数据
- 如果找不到精确匹配，系统会自动显示相似联系人

示例：
用户：帮我查一下张三的电话
你的回复：
好的，我来帮你查找。
{查询联系人:"张三"}

用户：有没有卡妈这个人
你的回复：
好的，我来帮你搜索一下。
{查询联系人:"卡妈"}

### 7. 查询电话号码
用户提供电话号码，想知道是谁的号码时，在末尾添加：
格式：{查询号码:"电话号码"}

说明：
- 你只需要提取电话号码即可
- 系统会在本地通讯录中搜索该号码对应的联系人
- 你不需要知道通讯录数据

示例：
用户：13912345678是谁的号码
你的回复：
我来帮你查一下这个号码。
{查询号码:"13912345678"}

### 8. 新建账本
用户要求创建或新建账本时，在末尾添加：
格式：{新建账本:"账本名称",单位:"单位名称"}

说明：单位是可选的，如果用户没有指定单位，则默认使用"元"。

示例：
用户：帮我新建一个餐饮账本
你的回复：
好的，已为你创建餐饮账本。
{新建账本:"餐饮",单位:"元"}

### 9. 删除账本
用户要求删除某个账本时，在末尾添加：
格式：{删除账本:"账本名称"}

示例：
用户：删除餐饮账本
你的回复：
好的，我来帮你删除餐饮账本。
{删除账本:"餐饮"}

### 10. 添加记账记录
用户要求添加记账记录时，在末尾添加：
格式：{记账:"账本名称",金额:"数字",备注:"备注内容",日期:"YYYY-MM-DD"}

说明：
- 金额：必须是数字，默认是支出
- 备注：可选，用户没有说则填空
- 日期：格式为 YYYY-MM-DD，只需要年月日，不需要具体时间
- 日期计算：支持"今天"=当天，"昨天"=前一天，"前天"=前两天，以此类推
- 如果用户没说"昨天晚上吃饭花了18.8"，则日期是昨天的日期，备注是"晚饭"，金额是18.8

示例1：
用户：记录一下昨天晚上吃饭花了18.8
你的回复：
好的，已为你记录。
{记账:"餐饮",金额:"18.8",备注:"晚饭",日期:"2026-06-23"}

示例2：
用户：今天早上买早餐花了5元
你的回复：
好的，已记录。
{记账:"餐饮",金额:"5",备注:"早餐",日期:"2026-06-24"}

### 11. 查询/匹配账本
用户提到某个账本或想查询、统计账本数据时，在末尾添加：
格式：{匹配账本:"关键词"}

说明：
- 当用户提到某个账本名称（如"餐饮"、"吃饭"、"干饭"等），使用此标记
- 系统会把所有账本名称发给你，由你来判断哪个账本最匹配
- 如果没有相似的账本，系统会自动新建一个

示例：
用户：帮我看看吃饭花了多少钱
你的回复：
好的，我来帮你查一下。
{匹配账本:"吃饭"}

用户：干饭这个月花了多少
你的回复：
好的，我来帮你统计。
{匹配账本:"干饭"}

### 12. 打开应用
当用户要求打开某个手机应用时，你需要判断：

**情况一：用户指定了具体的应用名称**
直接返回打开应用的标记：
格式：{打开应用:"应用名称"}

示例：
用户：帮我打开微信
你的回复：
好的，正在打开微信。
{打开应用:"微信"}

用户：打开暗区突围
你的回复：
好的，正在打开暗区突围。
{打开应用:"暗区突围"}


### 14. 手电筒控制
当用户要求打开或关闭手电筒时，在末尾添加标记：
- 打开手电筒格式：{手电筒:"开"}
- 关闭手电筒格式：{手电筒:"关"}

示例：
用户：打开手电筒
你的回复：
好的，正在为您打开手电筒。
{手电筒:"开"}

用户：关上手电筒
你的回复：
好的，正在为您关闭手电筒。
{手电筒:"关"}

用户：帮我开一下手电筒
你的回复：
好的，正在为您打开手电筒。
{手电筒:"开"}

用户：手电筒不用了
你的回复：
好的，正在为您关闭手电筒。
{手电筒:"关"}

### 15. 我想看影视内容（内置浏览器方案）
当用户说"我想看..."、"推荐电影/电视剧"、"上次看了什么"时，按以下三种情况处理：

**情况一：用户说出了具体的剧名/电影名**（如"我想看庆余年"、"我想看流浪地球2"）
通过搜索判断是具体剧名后，返回播放标记，格式：{播放:"剧名或电影名"}
说明：系统会用内置浏览器直接打开影视站搜索该内容，用户无需选择应用。

示例：
用户：我想看庆余年
你的回复：
好的，正在用内置浏览器为您搜索"庆余年"。
{播放:"庆余年"}

用户：我想看流浪地球2
你的回复：
好的，正在为您搜索"流浪地球2"。
{播放:"流浪地球2"}

**情况二：用户表达模糊的观影需求或要求推荐**（如"我想看电视剧"、"推荐点电影"、"想看好看的剧"）
第一步：返回查询待看标记，格式：{查询待看:"电影"或"电视剧"或"影视"}
说明：系统会统计本地待看文件中的数量。如果待看数量大于等于15个，系统直接弹出待看列表，本次对话结束；如果不足15个，系统会把"需要推荐:类型"发回给你。
第二步（仅在收到"需要推荐"时）：搜索15个热门的电影或电视剧（按用户指定的类型），返回推荐结果标记，格式：{推荐结果:"类型","名字1,名字2,名字3,...,名字15"}
注意：名字之间用英文逗号隔开，必须是15个，不要带书名号或其他符号。

示例：
用户：我想看电视剧
你的回复：
好的，我先看看您的待看列表。
{查询待看:"电视剧"}

（如果系统返回"需要推荐:电视剧"）
你的回复：
为您搜索到以下热门电视剧：
{推荐结果:"电视剧","庆余年,琅琊榜,甄嬛传,知否知否,欢乐颂,都挺好,小欢喜,三十而已,安家,隐秘的角落,沉默的真相,觉醒年代,山海情,人世间,狂飙"}

用户：推荐点电影
你的回复：
好的，我先看看您的待看列表。
{查询待看:"电影"}

**情况三：用户问"上次看了什么"、"最近看了什么"等查询已看历史**
返回查询已看标记，格式：{查询已看}
说明：系统会弹出最近3条已看记录。

示例：
用户：我上次看了什么
你的回复：
好的，为您查询最近观看记录。
{查询已看}

用户：最近看了什么电影
你的回复：
好的，为您查询最近观看记录。
{查询已看}

## 日期计算规则（非常重要，必须严格遵守）

注意：当前北京时间会在每次请求时作为上下文注入到用户最新消息开头，计算日期时请以该时间为准。

### 日程时间计算（含时分）：
- "今天" = 当前日期
- "明天" = 当前日期 + 1天
- "后天" = 当前日期 + 2天
- "大后天" = 当前日期 + 3天
- 早上/上午 = 默认8:00
- 中午 = 12:00
- 下午 = 默认14:00
- 晚上/傍晚 = 默认19:00
- 夜里/深夜 = 默认22:00
- 如果用户说"晚上9点"则是21:00
- 如果用户说"早上9点"则是9:00
- 如果只说时间没说日期且时间已过，则是明天这个时间

### 记账日期计算（只有年月日）：
- "今天" = 当前日期（YYYY-MM-DD
- "昨天" = 当前日期 - 1天
- "前天" = 当前日期 - 2天
- "大前天" = 当前日期 - 3天
- "今早/今天早上 = 今天
- 昨晚/昨天晚上 = 昨天
- 只说"早上"、"晚上"等没说日期的，默认是今天

## 重要提醒

1. 手电筒控制使用{手电筒:"开"}或{手电筒:"关"}标记
2. 特殊标记必须严格按照格式书写，不要有多余空格或符号
3. 特殊标记单独放在最后一行
4. 普通聊天不需要添加任何标记
5. 不要向用户询问通讯录或账本的具体数据，你只需要提取关键信息
        """.trimIndent()
    }

    private fun parseResponse(responseBody: String?): String {
        if (responseBody == null) return ""
        return try {
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val message = choices.getJSONObject(0).getJSONObject("message")
                message.getString("content")
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun extractAction(response: String): Pair<String, List<AIAction>> {
        val actions = mutableListOf<AIAction>()
        var processedResponse = response

        // 解析所有提醒格式：{提醒:"标题",时间:"YYYY-MM-DD HH:MM"}
        val reminderRegex = Regex("""\{提醒:"([^"]+)",时间:"([^"]+)"\}""")
        val reminderMatches = reminderRegex.findAll(response).toList()
        for (match in reminderMatches) {
            val title = match.groupValues[1]
            val timeStr = match.groupValues[2]
            val dateTime = parseDateTimeString(timeStr)
            actions.add(AIAction(
                type = ActionType.CREATE_EVENT,
                title = title,
                content = title,
                dateTime = dateTime,
                reminderMessage = "${title}时间到了"
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有设闹钟格式：{设闹钟:"标题",时间:"HH:MM",重复:"每天/工作日/周末/永不"}
        val alarmRegex = Regex("""\{设闹钟:"([^"]+)",时间:"(\d{1,2}):(\d{1,2})",重复:"(每天|工作日|周末|永不)"\}""")
        val alarmMatches = alarmRegex.findAll(response).toList()
        for (match in alarmMatches) {
            val title = match.groupValues[1]
            val hour = match.groupValues[2].toIntOrNull() ?: continue
            val minute = match.groupValues[3].toIntOrNull() ?: continue
            val repeatStr = match.groupValues[4]
            val repeatDays = when (repeatStr) {
                "每天" -> 127
                "工作日" -> 31
                "周末" -> 96
                "永不" -> 0
                else -> 0
            }
            actions.add(AIAction(
                type = ActionType.CREATE_ALARM,
                title = title,
                alarmHour = hour,
                alarmMinute = minute,
                alarmRepeatDays = repeatDays
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有笔记格式：{笔记:"标题",内容:"具体内容"}
        val noteRegex = Regex("""\{笔记:"([^"]+)",内容:"([^"]+)"\}""")
        val noteMatches = noteRegex.findAll(response).toList()
        for (match in noteMatches) {
            val title = match.groupValues[1]
            val content = match.groupValues[2]
            actions.add(AIAction(
                type = ActionType.CREATE_NOTE,
                title = title,
                content = content
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有联系人格式：{联系人:"姓名",号码:"电话号码"}
        val contactRegex = Regex("""\{联系人:"([^"]+)",号码:"([^"]+)"\}""")
        val contactMatches = contactRegex.findAll(response).toList()
        for (match in contactMatches) {
            val name = match.groupValues[1]
            val phone = match.groupValues[2]
            actions.add(AIAction(
                type = ActionType.CREATE_CONTACT,
                contactName = name,
                phoneNumber = phone
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有拨打电话格式：{拨打电话:"姓名或号码"}
        val callRegex = Regex("""\{拨打电话:"([^"]+)"\}""")
        val callMatches = callRegex.findAll(response).toList()
        for (match in callMatches) {
            val target = match.groupValues[1]
            val isPhoneNumber = target.matches(Regex("1[3-9]\\d{9}"))
            val action = if (isPhoneNumber) {
                AIAction(type = ActionType.CALL_PHONE, phoneNumber = target)
            } else {
                AIAction(type = ActionType.SEARCH_CONTACT_FOR_CALL, contactName = target)
            }
            actions.add(action)
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有删除联系人格式：{删除联系人:"姓名"}
        val deleteContactRegex = Regex("""\{删除联系人:"([^"]+)"\}""")
        val deleteContactMatches = deleteContactRegex.findAll(response).toList()
        for (match in deleteContactMatches) {
            val name = match.groupValues[1]
            actions.add(AIAction(type = ActionType.DELETE_CONTACT, contactName = name))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有查询联系人格式：{查询联系人:"姓名关键词"}
        val searchContactRegex = Regex("""\{查询联系人:"([^"]+)"\}""")
        val searchContactMatches = searchContactRegex.findAll(response).toList()
        for (match in searchContactMatches) {
            val name = match.groupValues[1]
            actions.add(AIAction(type = ActionType.SEARCH_CONTACT, contactName = name))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有查询号码格式：{查询号码:"电话号码"}
        val searchPhoneRegex = Regex("""\{查询号码:"([^"]+)"\}""")
        val searchPhoneMatches = searchPhoneRegex.findAll(response).toList()
        for (match in searchPhoneMatches) {
            val phone = match.groupValues[1]
            actions.add(AIAction(type = ActionType.SEARCH_PHONE_NUMBER, phoneNumber = phone))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有新建账本格式：{新建账本:"账本名称",单位:"单位名称"}
        val createLedgerRegex = Regex("""\{新建账本:"([^"]+)",单位:"([^"]*)"\}""")
        val createLedgerMatches = createLedgerRegex.findAll(response).toList()
        for (match in createLedgerMatches) {
            val name = match.groupValues[1]
            val unit = match.groupValues[2].ifBlank { "元" }
            actions.add(AIAction(type = ActionType.CREATE_LEDGER, ledgerName = name, ledgerUnit = unit))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有删除账本格式：{删除账本:"账本名称"}
        val deleteLedgerRegex = Regex("""\{删除账本:"([^"]+)"\}""")
        val deleteLedgerMatches = deleteLedgerRegex.findAll(response).toList()
        for (match in deleteLedgerMatches) {
            val name = match.groupValues[1]
            actions.add(AIAction(type = ActionType.DELETE_LEDGER, ledgerName = name))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有新记账格式：{记账:"账本名称",金额:"数字",备注:"备注内容",日期:"YYYY-MM-DD"}
        val newTransactionRegex = Regex("""\{记账:"([^"]+)",金额:"([^"]+)",备注:"([^"]*)",日期:"([^"]*)"\}""")
        val newTransactionMatches = newTransactionRegex.findAll(response).toList()
        for (match in newTransactionMatches) {
            val ledgerName = match.groupValues[1]
            val amountStr = match.groupValues[2]
            val note = match.groupValues[3]
            val dateStr = match.groupValues[4]
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val date = parseDateString(dateStr)
            actions.add(AIAction(
                type = ActionType.CREATE_TRANSACTION,
                ledgerName = ledgerName,
                transactionType = Transaction.TYPE_EXPENSE,
                transactionAmount = amount,
                transactionNote = note,
                transactionDate = date
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有匹配账本格式：{匹配账本:"关键词"}
        val matchLedgerRegex = Regex("""\{匹配账本:"([^"]+)"\}""")
        val matchLedgerMatches = matchLedgerRegex.findAll(response).toList()
        for (match in matchLedgerMatches) {
            val keyword = match.groupValues[1]
            actions.add(AIAction(type = ActionType.MATCH_LEDGER, matchKeyword = keyword))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析查询已看格式：{查询已看}
        val queryWatchedRegex = Regex("""\{查询已看\}""")
        val queryWatchedMatches = queryWatchedRegex.findAll(response).toList()
        for (match in queryWatchedMatches) {
            actions.add(AIAction(type = ActionType.QUERY_WATCHED))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析推荐结果格式：{推荐结果:"类型","名字1,名字2,..."}
        val recommendResultRegex = Regex("""\{推荐结果:"([^"]+)","([^"]+)"\}""")
        val recommendResultMatches = recommendResultRegex.findAll(response).toList()
        for (match in recommendResultMatches) {
            val type = match.groupValues[1]
            val namesStr = match.groupValues[2]
            val names = namesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            actions.add(AIAction(
                type = ActionType.SHOW_RECOMMENDATIONS,
                movieType = type,
                movieTitles = names
            ))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析查询待看格式：{查询待看:"类型"}
        val queryPendingRegex = Regex("""\{查询待看:"([^"]+)"\}""")
        val queryPendingMatches = queryPendingRegex.findAll(response).toList()
        for (match in queryPendingMatches) {
            val type = match.groupValues[1]
            actions.add(AIAction(type = ActionType.QUERY_PENDING, movieType = type))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析播放格式：{播放:"剧名或电影名"}
        val playRegex = Regex("""\{播放:"([^"]+)"\}""")
        val playMatches = playRegex.findAll(response).toList()
        for (match in playMatches) {
            val title = match.groupValues[1]
            actions.add(AIAction(type = ActionType.PLAY_IN_BROWSER, movieTitle = title))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析所有打开应用格式：{打开应用:"应用名称"}
        val openAppRegex = Regex("""\{打开应用:"([^"]+)"\}""")
        val openAppMatches = openAppRegex.findAll(response).toList()
        for (match in openAppMatches) {
            val appName = match.groupValues[1]
            actions.add(AIAction(type = ActionType.OPEN_APP, appName = appName))
            processedResponse = processedResponse.replace(match.value, "")
        }

        // 解析手电筒控制格式：{手电筒:"开"或"关"}
        val flashlightRegex = Regex("""\{手电筒:"(开|关)"\}""")
        val flashlightMatches = flashlightRegex.findAll(response).toList()
        for (match in flashlightMatches) {
            val state = match.groupValues[1]
            if (state == "开") {
                actions.add(AIAction(type = ActionType.FLASHLIGHT_ON))
            } else {
                actions.add(AIAction(type = ActionType.FLASHLIGHT_OFF))
            }
            processedResponse = processedResponse.replace(match.value, "")
        }

        val displayText = processedResponse.trim()
        return Pair(displayText, actions)
    }

    private fun parseDateString(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            format.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            val cal = java.util.Calendar.getInstance()
            cal.time = format.parse(dateStr) ?: Date()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseDateTimeString(timeStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            format.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            format.parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val format2 = SimpleDateFormat("yyyy-MM-dd H:mm", Locale.CHINA)
                format2.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                format2.parse(timeStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    fun parseDateTime(dateTimeStr: String): Long? {
        val now = Date()
        val calendar = java.util.Calendar.getInstance()
        calendar.time = now

        return try {
            when {
                dateTimeStr.contains("明天") -> {
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                    extractTime(dateTimeStr, calendar)
                }
                dateTimeStr.contains("后天") -> {
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, 2)
                    extractTime(dateTimeStr, calendar)
                }
                dateTimeStr.contains("今天") -> {
                    extractTime(dateTimeStr, calendar)
                }
                else -> {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    format.parse(dateTimeStr)?.time
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTime(str: String, calendar: java.util.Calendar): Long {
        val timeRegex = Regex("(\\d{1,2})[点时:](\\d{2})?")
        val matchResult = timeRegex.find(str)

        if (matchResult != null) {
            val hour = matchResult.groupValues[1].toInt()
            val minute = if (matchResult.groupValues.size > 2 && matchResult.groupValues[2].isNotEmpty()) {
                matchResult.groupValues[2].toInt()
            } else {
                0
            }

            var finalHour = hour
            if (str.contains("下午") || str.contains("晚上") || str.contains("傍晚")) {
                if (hour < 12) finalHour = hour + 12
            }

            calendar.set(java.util.Calendar.HOUR_OF_DAY, finalHour)
            calendar.set(java.util.Calendar.MINUTE, minute)
            calendar.set(java.util.Calendar.SECOND, 0)

            if (calendar.timeInMillis < System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            return calendar.timeInMillis
        }

        return calendar.timeInMillis
    }
}
