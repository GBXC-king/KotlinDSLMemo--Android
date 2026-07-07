package com.example.memo.agent

/**
 * 工具执行结果
 * 统一工具返回值的格式，让 AI 更容易理解执行结果
 */
data class ToolResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null,  // 可选的结构化数据
    val hint: String? = null              // 可选的下一步建议
) {
    /**
     * 格式化为 AI 可读的字符串
     */
    fun toObservation(): String {
        val sb = StringBuilder()
        
        // 状态
        sb.append(if (success) "[成功]" else "[失败]")
        sb.append(" ")
        
        // 消息
        sb.append(message)
        
        // 结构化数据
        if (!data.isNullOrEmpty()) {
            sb.append("\n数据:")
            data.forEach { (key, value) ->
                sb.append("\n  $key: $value")
            }
        }
        
        // 建议
        if (!hint.isNullOrBlank()) {
            sb.append("\n💡 $hint")
        }
        
        return sb.toString()
    }
    
    companion object {
        /**
         * 快捷创建成功结果
         */
        fun success(message: String, data: Map<String, Any>? = null, hint: String? = null) =
            ToolResult(success = true, message = message, data = data, hint = hint)
        
        /**
         * 快捷创建失败结果
         */
        fun failure(message: String, hint: String? = null) =
            ToolResult(success = false, message = message, hint = hint)
    }
}
